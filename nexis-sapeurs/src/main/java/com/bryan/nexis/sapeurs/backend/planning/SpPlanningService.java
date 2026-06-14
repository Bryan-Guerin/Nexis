package com.bryan.nexis.sapeurs.backend.planning;

import com.bryan.nexis.core.backend.AbstractPlanningService;
import com.bryan.nexis.core.backend.dto.PlanningDto;
import com.bryan.nexis.core.datamodel.TypeService;
import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.datamodel.SpPlanning;
import com.bryan.nexis.sapeurs.datamodel.SpPlanningStatut;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import com.bryan.nexis.sapeurs.datarepository.SpPlanningRepository;
import com.bryan.nexis.sapeurs.datarepository.SpPlanningStatutRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.jpa.repository.JpaRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

@Singleton
public class SpPlanningService extends AbstractPlanningService<SpPlanning> {

    private static final Logger log = LoggerFactory.getLogger(SpPlanningService.class);
    private static final long UNE_HEURE_MS = 3_600_000L;

    private final SpPlanningRepository       planningRepo;
    private final SpMembreRepository         membreRepo;
    private final SpPlanningStatutRepository statutRepo;
    private final ApplicationEventPublisher<RealtimeEvent> events;

    public SpPlanningService(SpPlanningRepository planningRepo, SpMembreRepository membreRepo,
                             SpPlanningStatutRepository statutRepo,
                             ApplicationEventPublisher<RealtimeEvent> events) {
        this.planningRepo = planningRepo;
        this.membreRepo   = membreRepo;
        this.statutRepo   = statutRepo;
        this.events       = events;
    }

    /** Le membre est-il actuellement de garde (plage GARDE couvrant maintenant) ? */
    @jakarta.transaction.Transactional
    public boolean estDeGarde(UUID membreId) {
        return !planningRepo.findMembreCouvrant(membreId, TypeService.GARDE, Instant.now()).isEmpty();
    }

    /** Catégorie de service en cours du membre (GARDE / ASTREINTE / null). */
    @Transactional
    public String categorieCouranteService(UUID membreId) {
        var now = Instant.now();
        if (!planningRepo.findMembreCouvrant(membreId, TypeService.GARDE, now).isEmpty()) return "GARDE";
        if (!planningRepo.findMembreCouvrant(membreId, TypeService.ASTREINTE, now).isEmpty()) return "ASTREINTE";
        return null;
    }

    /**
     * Démarre une garde « maintenant » pour un membre (action dispatch). Durée par défaut bornée
     * en heures ; même arrondi/non-rétroactivité que l'auto-déclaration.
     */
    @Transactional
    public PlanningDto prendreGardePour(UUID membreId, int heures) {
        int h = Math.max(1, heures);
        var statut = premierStatut(TypeService.GARDE);
        var now = Instant.now();
        return declareSelf(membreId, statut.getId(), now, now.plusMillis(h * UNE_HEURE_MS), null);
    }

    /** Termine la garde en cours du membre (fin arrondie au quart sup., départ effectif = maintenant). */
    @Transactional
    public PlanningDto terminerGardeEnCours(UUID membreId) {
        var now = Instant.now();
        var plage = planningRepo.findMembreCouvrant(membreId, TypeService.GARDE, now).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune garde en cours à terminer."));
        // Fin = maintenant (plus de quart d'heure entamé) ; quitteLe trace le départ effectif.
        plage.setFin(now.isAfter(plage.getDebut()) ? now : plage.getFin());
        plage.setQuitteLe(now);
        var saved = planningRepo.update(plage);
        log.info("Fin de garde anticipée : membre={} fin payée={} quitte={}", membreId, plage.getFin(), now);
        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.PLANNING, "SP",
                plage.getMembre() + " — fin de garde",
                Map.of("membreId", membreId.toString()), plage.getMembreUsername()));
        return PlanningDto.from(saved);
    }

    /**
     * Bascule l'astreinte en cours en garde : le reste du créneau passe en garde
     * (minimum 1 h, le créneau est prolongé si nécessaire).
     */
    @Transactional
    public PlanningDto basculerVersGarde(UUID membreId) {
        var now = Instant.now();
        var astreinte = planningRepo.findMembreCouvrant(membreId, TypeService.ASTREINTE, now).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune astreinte en cours."));
        Instant split = now;
        Instant minFin = split.plusMillis(UNE_HEURE_MS);
        Instant gardeFin = astreinte.getFin().isAfter(minFin) ? astreinte.getFin() : minFin;   // min 1 h
        if (split.isAfter(astreinte.getDebut())) { astreinte.setFin(split); planningRepo.update(astreinte); }
        else { planningRepo.delete(astreinte); }
        var garde = new SpPlanning(astreinte.getMembre(), split, gardeFin, premierStatut(TypeService.GARDE));
        var saved = planningRepo.save(garde);
        log.info("Bascule astreinte→garde : membre={} garde {} → {}", membreId, split, gardeFin);
        publierBascule(garde, "passage en garde");
        return PlanningDto.from(saved);
    }

    /**
     * Bascule la garde en cours en astreinte : le temps restant passe en astreinte
     * (sans prolonger). Le quart d'heure entamé reste compté en garde.
     */
    @Transactional
    public PlanningDto basculerVersAstreinte(UUID membreId) {
        var now = Instant.now();
        var garde = planningRepo.findMembreCouvrant(membreId, TypeService.GARDE, now).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune garde en cours."));
        Instant split = now;
        Instant gardeFin = garde.getFin();
        if (!split.isBefore(gardeFin)) {
            throw new IllegalStateException("Garde trop courte pour basculer en astreinte (elle se termine bientôt).");
        }
        garde.setFin(split);
        garde.setQuitteLe(now);   // plus en service dès maintenant (quart entamé payé en garde)
        planningRepo.update(garde);
        var astreinte = new SpPlanning(garde.getMembre(), split, gardeFin, premierStatut(TypeService.ASTREINTE));
        var saved = planningRepo.save(astreinte);
        log.info("Bascule garde→astreinte : membre={} astreinte {} → {}", membreId, split, gardeFin);
        publierBascule(garde, "passage en astreinte");
        return PlanningDto.from(saved);
    }

    /** Déclaration d'une plage pour un membre par le dispatch (drag & drop). */
    @Transactional
    public PlanningDto declarerPour(UUID membreId, UUID statutId, Instant debut, Instant fin) {
        return declareSelf(membreId, statutId, debut, fin, null);
    }

    /**
     * Modifie une plage (déplacement / redimensionnement / changement de statut). Réécrit la plage
     * (delete + create) afin de réappliquer la fusion des chevauchements du même statut.
     * {@code exigeMembreId} non null = contrôle de propriété (un effectif n'édite que ses plages).
     */
    @Transactional
    public PlanningDto modifier(UUID id, UUID statutId, Instant debut, Instant fin, UUID exigeMembreId) {
        var plage = planningRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Plage de planning introuvable : " + id));
        verifierProprietaire(plage, exigeMembreId);
        if (debut == null || fin == null) throw new IllegalArgumentException("Début et fin requis.");
        if (!fin.isAfter(debut)) throw new IllegalArgumentException("La fin doit être postérieure au début.");
        UUID membreId = plage.getMembre().getId();
        UUID sId = statutId != null ? statutId : plage.getStatut().getId();
        planningRepo.delete(plage);
        return create(membreId, sId, debut, fin, null);
    }

    /** Supprime une plage. {@code exigeMembreId} non null = contrôle de propriété. */
    @Transactional
    public void supprimer(UUID id, UUID exigeMembreId) {
        var plage = planningRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Plage de planning introuvable : " + id));
        verifierProprietaire(plage, exigeMembreId);
        planningRepo.delete(plage);
        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.PLANNING, "SP",
                plage.getMembre() + " — créneau supprimé",
                Map.of("membreId", plage.getMembre().getId().toString()), plage.getMembreUsername()));
    }

    private void verifierProprietaire(SpPlanning plage, UUID exigeMembreId) {
        if (exigeMembreId != null && !plage.getMembre().getId().equals(exigeMembreId)) {
            throw new IllegalStateException("Action non autorisée : créneau d'un autre effectif.");
        }
    }

    private SpPlanningStatut premierStatut(TypeService categorie) {
        return statutRepo.findAll().stream().filter(s -> s.getCategorie() == categorie)
                .min(Comparator.comparingInt(SpPlanningStatut::getPosition))
                .orElseThrow(() -> new IllegalStateException("Aucun statut de planning « " + categorie + " » configuré."));
    }

    private void publierBascule(SpPlanning p, String message) {
        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.PLANNING, "SP",
                p.getMembre() + " — " + message,
                Map.of("membreId", p.getMembreId().toString()), p.getMembreUsername()));
    }

    /** Diffuse l'événement (→ rafraîchit dispatch/dashboard, et trace en main courante). */
    @Override
    protected void onCreated(SpPlanning p) {
        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.PLANNING, "SP",
                p.getMembre() + " — " + p.getStatut().getLabel() + " déclaré",
                Map.of("membreId", p.getMembreId().toString()),
                p.getMembreUsername()));
    }

    @Override protected JpaRepository<SpPlanning, UUID> repo() { return planningRepo; }

    @Override protected List<SpPlanning> findByMembre(UUID membreId) {
        return planningRepo.findByMembreId(membreId);
    }

    @Override protected List<SpPlanning> findOverlapping(UUID membreId, Instant debut, Instant fin) {
        return planningRepo.findOverlapping(membreId, debut, fin);
    }

    @Override protected List<UUID> findMembreIdsEnService(Instant now) {
        return planningRepo.findMembreIdsEnService(now, TypeService.GARDE);
    }

    @Override protected SpPlanning build(UUID membreId, UUID statutId, Instant debut, Instant fin, String notes) {
        var membre = membreRepo.findById(membreId)
                .orElseThrow(() -> new NoSuchElementException("Membre SP introuvable : " + membreId));
        var statut = statutRepo.findById(statutId)
                .orElseThrow(() -> new NoSuchElementException("Statut planning introuvable : " + statutId));
        var planning = new SpPlanning(membre, debut, fin, statut);
        planning.setNotes(notes);
        return planning;
    }
}

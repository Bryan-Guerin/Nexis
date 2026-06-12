package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeAffectationDto;
import com.bryan.nexis.sapeurs.backend.planning.SpPlanningService;
import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeAffectation;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypePosteRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class SpVehiculeAffectationService {

    private static final Logger log = LoggerFactory.getLogger(SpVehiculeAffectationService.class);

    /** Type d'événement (ciblé) : garde terminée mais membre toujours engagé sur une intervention. */
    private static final String GARDE_FIN_INTERVENTION = "GARDE_FIN_INTERVENTION";

    private final SpVehiculeAffectationRepository affectationRepo;
    private final SpVehiculeRepository vehiculeRepo;
    private final SpMembreRepository membreRepo;
    private final SpVehiculeTypePosteRepository posteRepo;
    private final SpInterventionRepository interventionRepo;
    private final SpPlanningService planningService;
    private final ApplicationEventPublisher<RealtimeEvent> events;
    private final SecurityService securityService;

    /** Membres déjà notifiés « garde finie mais en intervention » (évite de re-notifier à chaque tick). */
    private final Set<UUID> notifiedOnInter = ConcurrentHashMap.newKeySet();

    public SpVehiculeAffectationService(SpVehiculeAffectationRepository affectationRepo,
                                        SpVehiculeRepository vehiculeRepo,
                                        SpMembreRepository membreRepo,
                                        SpVehiculeTypePosteRepository posteRepo,
                                        SpInterventionRepository interventionRepo,
                                        SpPlanningService planningService,
                                        ApplicationEventPublisher<RealtimeEvent> events,
                                        SecurityService securityService) {
        this.affectationRepo = affectationRepo;
        this.vehiculeRepo = vehiculeRepo;
        this.membreRepo = membreRepo;
        this.posteRepo = posteRepo;
        this.interventionRepo = interventionRepo;
        this.planningService = planningService;
        this.events = events;
        this.securityService = securityService;
    }

    private String actor() { return securityService.username().orElse(null); }

    @Transactional
    public List<SpVehiculeAffectationDto> findActives() {
        return affectationRepo.findByFinIsNull().stream().map(SpVehiculeAffectationDto::from).toList();
    }

    @Transactional
    public SpVehiculeAffectationDto affecter(UUID vehiculeId, UUID membreId, UUID posteId, Instant debut) {
        var vehicule = vehiculeRepo.findById(vehiculeId)
                .orElseThrow(() -> new NoSuchElementException("Véhicule SP introuvable : " + vehiculeId));
        var membre = membreRepo.findById(membreId)
                .orElseThrow(() -> new NoSuchElementException("Membre SP introuvable : " + membreId));
        var poste = posteRepo.findById(posteId)
                .orElseThrow(() -> new NoSuchElementException("Poste introuvable : " + posteId));

        // Le poste appartient bien au type du véhicule affecté
        if (!poste.getVehiculeType().getId().equals(vehicule.getType().getId())) {
            throw new IllegalStateException("Le poste " + poste.getFonction().getCode()
                    + " n'appartient pas au type du véhicule " + vehicule.getLibelle());
        }

        // Double affectation autorisée (ex. FSR + VTU), mais pas deux fois sur le MÊME véhicule.
        boolean dejaSurCeVehicule = affectationRepo.findByMembreIdAndFinIsNull(membreId).stream()
                .anyMatch(a -> a.getVehicule().getId().equals(vehiculeId));
        if (dejaSurCeVehicule) {
            throw new IllegalStateException("Le membre est déjà engagé sur ce véhicule.");
        }

        // Seul un membre actuellement de garde peut être affecté à un véhicule
        if (!planningService.estDeGarde(membreId)) {
            throw new IllegalStateException("Le membre n'est pas de garde : affectation impossible.");
        }

        // Le membre est qualifié pour la FONCTION requise par le poste
        boolean qualifie = membre.getQualifications().stream()
                .anyMatch(q -> q.getFonction().getId().equals(poste.getFonction().getId()));
        if (!qualifie) {
            throw new IllegalStateException("Le membre " + membreId
                    + " n'est pas qualifié pour la fonction " + poste.getFonction().getCode());
        }

        long occupes = affectationRepo.countByVehiculeIdAndPosteIdAndFinIsNull(vehiculeId, posteId);
        if (occupes >= poste.getNbPlaces()) {
            throw new IllegalStateException("Capacité du poste " + poste.getFonction().getCode() + " atteinte (" + poste.getNbPlaces() + "/" + poste.getNbPlaces() + ")");
        }

        var saved = affectationRepo.save(new SpVehiculeAffectation(vehicule, membre, poste, debut));

        log.info("Affectation : {} → {} (poste {})", membre, vehicule, poste.getFonction().getCode());
        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.AFFECTATION, "SP",
                membre + " affecté à " + vehicule,
                Map.of("vehiculeId", vehiculeId.toString(), "membreId", membreId.toString()), actor()));

        return SpVehiculeAffectationDto.from(saved);
    }

    @Transactional
    public SpVehiculeAffectationDto cloturer(UUID affectationId, Instant fin) {
        var affectation = affectationRepo.findById(affectationId)
                .orElseThrow(() -> new NoSuchElementException("Affectation SP introuvable : " + affectationId));
        affectation.setFin(fin);
        var updated = affectationRepo.update(affectation);

        log.info("Désaffectation : {} retiré de {}", affectation.getMembre(), affectation.getVehicule());
        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.DESAFFECTATION, "SP",
                affectation.getMembre() + " retiré de " + affectation.getVehicule(),
                Map.of("vehiculeId", affectation.getVehicule().getId().toString(),
                       "membreUsername", affectation.getMembre().getUser().getUsername()), actor()));

        return SpVehiculeAffectationDto.from(updated);
    }

    /**
     * Désaffecte tout le personnel embarqué (bouton dispatch), en ÉPARGNANT les équipages
     * des engins actuellement engagés sur une intervention ouverte (même garde-fou que le sweep).
     */
    @Transactional
    public int cloturerToutes(Instant fin) {
        Set<UUID> enginsEngages = interventionRepo.findByFinIsNull().stream()
                .flatMap(i -> i.getEngins().stream())
                .map(SpVehicule::getId)
                .collect(Collectors.toSet());
        var actives = affectationRepo.findByFinIsNull().stream()
                .filter(a -> !enginsEngages.contains(a.getVehicule().getId()))
                .toList();
        for (var aff : actives) {
            aff.setFin(fin);
            affectationRepo.update(aff);
        }
        if (!actives.isEmpty()) {
            log.info("Désaffectation globale : {} affectation(s) clôturée(s)", actives.size());
            events.publishEvent(RealtimeEvent.faction(RealtimeEvent.DESAFFECTATION, "SP",
                    actives.size() + " effectif(s) désaffecté(s) (désaffectation globale)", Map.of(), actor()));
        }
        return actives.size();
    }

    /**
     * Clôture les affectations des membres qui ne sont plus de garde — qu'il s'agisse
     * d'une fin de garde manuelle OU de l'expiration automatique du créneau de planning
     * (pas d'event/CRON propre à l'expiration : on s'appuie sur {@code estDeGarde}, recalculé).
     * Appelé périodiquement par {@link SpAffectationSweeper}.
     */
    @Transactional
    public int cloturerExpirees() {
        var now = Instant.now();

        // Véhicules engagés sur une intervention OUVERTE : leur équipage n'est PAS désaffecté.
        Set<UUID> enginsEngages = interventionRepo.findByFinIsNull().stream()
                .flatMap(i -> i.getEngins().stream())
                .map(SpVehicule::getId)
                .collect(Collectors.toSet());

        int closed = 0;
        Set<UUID> enInterSansGarde = new HashSet<>();   // membres dans l'état "garde finie + en inter"

        for (var aff : affectationRepo.findByFinIsNull()) {
            if (planningService.estDeGarde(aff.getMembre().getId())) {
                continue; // toujours de garde → rien à faire
            }

            if (enginsEngages.contains(aff.getVehicule().getId())) {
                // Garde-fou : garde terminée mais véhicule en intervention → on conserve l'affectation
                // et on prévient le membre concerné (une seule fois tant qu'il reste dans cet état).
                UUID membreId = aff.getMembre().getId();
                enInterSansGarde.add(membreId);
                if (notifiedOnInter.add(membreId)) {
                    String username = aff.getMembre().getUser().getUsername();
                    events.publishEvent(RealtimeEvent.users(GARDE_FIN_INTERVENTION, "SP", Set.of(username),
                            "Votre garde est terminée mais vous êtes engagé sur une intervention ("
                                    + aff.getVehicule().getLibelle() + ") : vous restez affecté jusqu'au retour.",
                            Map.of("membreUsername", username,
                                   "vehiculeId", aff.getVehicule().getId().toString()), null));
                }
            } else {
                aff.setFin(now);
                affectationRepo.update(aff);
                closed++;
                events.publishEvent(RealtimeEvent.faction(RealtimeEvent.DESAFFECTATION, "SP",
                        aff.getMembre() + " désaffecté (fin de garde)",
                        Map.of("vehiculeId", aff.getVehicule().getId().toString(),
                               "membreUsername", aff.getMembre().getUser().getUsername()), null));
            }
        }

        // Oublie les membres qui ont quitté l'état "garde finie + en inter" (désaffectés ou redevenus
        // de garde) → la notif pourra se redéclencher s'ils y reviennent plus tard.
        notifiedOnInter.retainAll(enInterSansGarde);

        if (closed > 0) log.info("Sweep fin de garde : {} affectation(s) clôturée(s)", closed);
        return closed;
    }

    /** Bip simple de l'équipage actif d'un véhicule (bouton dispatch). */
    @Transactional
    public int bip(UUID vehiculeId) {
        var vehicule = vehiculeRepo.findById(vehiculeId)
                .orElseThrow(() -> new NoSuchElementException("Véhicule SP introuvable : " + vehiculeId));
        var payload = new java.util.HashMap<String, String>();
        payload.put("vehicule", vehicule.getLibelle());
        payload.put("vehiculeId", vehicule.getId().toString());
        return bipCrew(vehicule, "🔔 Bip — " + vehicule, payload, null);
    }

    /** Bip de l'équipage actif d'un véhicule avec message + charge utile (ex. infos de départ). */
    public int bipCrew(SpVehicule vehicule, String message, Map<String, String> payload, String reference) {
        Set<String> equipage = affectationRepo.findByVehiculeIdAndFinIsNull(vehicule.getId()).stream()
                .map(a -> a.getMembre().getUser().getUsername())
                .collect(Collectors.toSet());
        if (!equipage.isEmpty()) {
            var ev = RealtimeEvent.users(RealtimeEvent.BIP, "SP", equipage, message, payload, actor());
            if (reference != null) ev.withReference(reference);
            events.publishEvent(ev);
        }
        log.debug("Bip {} → {} destinataire(s)", vehicule.getLibelle(), equipage.size());
        return equipage.size();
    }
}

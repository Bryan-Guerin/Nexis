package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeAffectationDto;
import com.bryan.nexis.sapeurs.backend.planning.SpPlanningService;
import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeAffectation;
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
import java.util.stream.Collectors;

@Singleton
public class SpVehiculeAffectationService {

    private static final Logger log = LoggerFactory.getLogger(SpVehiculeAffectationService.class);

    private final SpVehiculeAffectationRepository affectationRepo;
    private final SpVehiculeRepository vehiculeRepo;
    private final SpMembreRepository membreRepo;
    private final SpVehiculeTypePosteRepository posteRepo;
    private final SpPlanningService planningService;
    private final ApplicationEventPublisher<RealtimeEvent> events;
    private final SecurityService securityService;

    public SpVehiculeAffectationService(SpVehiculeAffectationRepository affectationRepo,
                                        SpVehiculeRepository vehiculeRepo,
                                        SpMembreRepository membreRepo,
                                        SpVehiculeTypePosteRepository posteRepo,
                                        SpPlanningService planningService,
                                        ApplicationEventPublisher<RealtimeEvent> events,
                                        SecurityService securityService) {
        this.affectationRepo = affectationRepo;
        this.vehiculeRepo = vehiculeRepo;
        this.membreRepo = membreRepo;
        this.posteRepo = posteRepo;
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

        // Un membre déjà engagé sur un véhicule ne peut pas l'être sur un autre
        if (!affectationRepo.findByMembreIdAndFinIsNull(membreId).isEmpty()) {
            throw new IllegalStateException("Le membre est déjà engagé sur un véhicule.");
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

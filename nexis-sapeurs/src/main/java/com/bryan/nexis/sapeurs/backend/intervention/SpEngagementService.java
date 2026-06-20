package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.dto.DesaffectationPreviewDto;
import com.bryan.nexis.sapeurs.backend.vehicule.SpVehiculeAffectationService;
import com.bryan.nexis.sapeurs.datamodel.SpIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeStatut;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeTypePoste;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeEtatRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeStatutRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypePosteRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Logique d'engagement (« qui part, qui se détache, postes obligatoires »), support de
 * l'armement. Service <b>fondamental</b> : aucune dépendance vers {@link SpInterventionService}
 * (pas de cycle). Consommé par l'orchestrateur (Intervention) mais aussi par le calcul d'armement
 * ({@code SpVehiculeService}) et l'affectation automatique.
 */
@Singleton
public class SpEngagementService {

    private static final Logger log = LoggerFactory.getLogger(SpEngagementService.class);

    private final SpInterventionRepository        interventionRepo;
    private final SpVehiculeAffectationRepository affectationRepo;
    private final SpVehiculeTypePosteRepository   posteRepo;
    private final SpVehiculeRepository            vehiculeRepo;
    private final SpVehiculeStatutRepository      statutRepo;
    private final SpVehiculeEtatRepository        etatRepo;
    private final SpVehiculeAffectationService    affectationService;   // bip + désaffectation
    private final ApplicationEventPublisher<RealtimeEvent> events;
    private final SecurityService                 securityService;

    public SpEngagementService(SpInterventionRepository interventionRepo,
                               SpVehiculeAffectationRepository affectationRepo,
                               SpVehiculeTypePosteRepository posteRepo, SpVehiculeRepository vehiculeRepo,
                               SpVehiculeStatutRepository statutRepo, SpVehiculeEtatRepository etatRepo,
                               SpVehiculeAffectationService affectationService,
                               ApplicationEventPublisher<RealtimeEvent> events, SecurityService securityService) {
        this.interventionRepo   = interventionRepo;
        this.affectationRepo    = affectationRepo;
        this.posteRepo          = posteRepo;
        this.vehiculeRepo       = vehiculeRepo;
        this.statutRepo         = statutRepo;
        this.etatRepo           = etatRepo;
        this.affectationService = affectationService;
        this.events             = events;
        this.securityService    = securityService;
    }

    private String actor() { return securityService.username().orElse(null); }

    /**
     * Membres actuellement engagés sur une intervention OUVERTE via un véhicule AUTRE que celui exclu.
     * Sert au calcul d'armement : un membre occupé ailleurs ne couvre plus un poste obligatoire.
     */
    @Transactional
    public Set<UUID> membresOccupesSurAutreIntervention(UUID vehiculeIdExclu) {
        var enginsEngages = interventionRepo.findByFinIsNull().stream()
                .flatMap(i -> i.getEngins().stream())
                .map(SpVehicule::getId)
                .filter(vid -> !vid.equals(vehiculeIdExclu))
                .collect(Collectors.toSet());
        return enginsEngages.stream()
                .flatMap(vid -> affectationRepo.findByVehiculeIdAndFinIsNull(vid).stream())
                .map(a -> a.getMembre().getId())
                .collect(Collectors.toSet());
    }

    /**
     * Aperçu des effectifs qui seraient désaffectés au déclenchement : ceux qui tiennent un poste
     * NON obligatoire d'un engin sélectionné tout en étant occupés ailleurs (intervention en cours
     * ou autre véhicule du même départ combiné). Un équipier disponible n'est jamais retiré.
     */
    @Transactional
    public List<DesaffectationPreviewDto> previewDesaffectationNonObligatoire(List<UUID> vehiculeIds) {
        var result = new ArrayList<DesaffectationPreviewDto>();
        if (vehiculeIds == null) return result;
        for (var vid : vehiculeIds) {
            var v = vehiculeRepo.findById(vid).orElse(null);
            if (v == null) continue;
            var oblig = obligPosteIds(v);
            if (oblig.isEmpty()) continue;   // aucun poste obligatoire → on ne désaffecte personne
            var occupes = new HashSet<>(membresOccupesSurAutreIntervention(vid));
            for (var autre : vehiculeIds) {
                if (autre.equals(vid)) continue;
                affectationRepo.findByVehiculeIdAndFinIsNull(autre)
                        .forEach(a -> occupes.add(a.getMembre().getId()));
            }
            for (var a : affectationRepo.findByVehiculeIdAndFinIsNull(vid)) {
                if (a.getPoste() != null && !oblig.contains(a.getPoste().getId())
                        && occupes.contains(a.getMembre().getId())) {
                    var m = a.getMembre();
                    result.add(new DesaffectationPreviewDto(v.getLibelle(), m.getGrade().getCode(),
                            m.getNomComplet() != null ? m.getNomComplet() : m.getUser().getUsername(),
                            a.getPoste().getFonction().getLabel()));
                }
            }
        }
        return result;
    }

    private Set<UUID> obligPosteIds(SpVehicule v) {
        return posteRepo.findByVehiculeTypeId(v.getType().getId()).stream()
                .filter(SpVehiculeTypePoste::isObligatoire)
                .map(SpVehiculeTypePoste::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Bloque le départ si un poste OBLIGATOIRE d'un engin est tenu par un effectif déjà engagé
     * sur une autre intervention (départ combiné : le premier véhicule est parti avec lui).
     */
    public void verifierEquipageObligatoireDisponible(List<SpVehicule> engins) {
        for (var engin : engins) {
            var oblig = obligPosteIds(engin);
            if (oblig.isEmpty()) continue;
            var occupes = membresOccupesSurAutreIntervention(engin.getId());
            for (var a : affectationRepo.findByVehiculeIdAndFinIsNull(engin.getId())) {
                if (a.getPoste() != null && oblig.contains(a.getPoste().getId())
                        && occupes.contains(a.getMembre().getId())) {
                    String interBloquante = codeInterventionDuMembre(a.getMembre().getId());
                    log.info("Départ de {} bloqué : {} (poste obligatoire {}) déjà engagé sur {}",
                            engin, a.getMembre(), a.getPoste().getFonction().getCode(), interBloquante);
                    throw new IllegalStateException("Départ de " + engin + " impossible : "
                            + a.getMembre() + " (poste obligatoire " + a.getPoste().getFonction().getCode()
                            + ") est déjà engagé sur " + interBloquante + ".");
                }
            }
        }
    }

    /** Code de l'intervention ouverte sur laquelle ce membre est engagé (via ses affectations). */
    private String codeInterventionDuMembre(UUID membreId) {
        var sesVehicules = affectationRepo.findByMembreIdAndFinIsNull(membreId).stream()
                .map(a -> a.getVehicule().getId())
                .collect(Collectors.toSet());
        return interventionRepo.findByFinIsNull().stream()
                .filter(i -> i.getEngins().stream().anyMatch(e -> sesVehicules.contains(e.getId())))
                .map(SpIntervention::getCode)
                .findFirst().orElse("une intervention en cours");
    }

    /**
     * Désaffecte, au déclenchement, les équipiers sur poste NON obligatoire d'un engin qui sont
     * occupés ailleurs (autre intervention en cours — y compris celle qu'on vient de créer, pour
     * les autres engins du départ combiné). Un équipier disponible part normalement avec l'engin.
     */
    public void desaffecterPostesNonObligatoires(List<SpVehicule> engins, String reference) {
        var now = Instant.now();
        for (var engin : engins) {
            var oblig = obligPosteIds(engin);
            if (oblig.isEmpty()) continue;   // aucun poste obligatoire → on ne désaffecte personne
            var occupes = membresOccupesSurAutreIntervention(engin.getId());
            for (var a : affectationRepo.findByVehiculeIdAndFinIsNull(engin.getId())) {
                if (a.getPoste() != null && !oblig.contains(a.getPoste().getId())
                        && occupes.contains(a.getMembre().getId())) {
                    affectationService.cloturer(a.getId(), now);
                }
            }
        }
    }

    /**
     * Déclenchement : bascule chaque engin sur le premier statut (Déclenché) + état Indisponible,
     * puis bipe l'équipage avec les infos de départ.
     */
    public void engager(List<SpVehicule> engins, SpIntervention inter) {
        log.debug("Engagement de {} engin(s) sur {}", engins.size(), inter.getCode());
        SpVehiculeStatut declenche = statutRepo.listOrderByPositionAsc().stream().findFirst().orElse(null);
        var indispo = etatRepo.findByCode("INDISPONIBLE").orElse(null);
        for (var engin : engins) {
            if (declenche != null) {
                engin.setStatut(declenche);
                engin.setEtat(indispo != null ? indispo : declenche.getEtat());
                vehiculeRepo.update(engin);
                events.publishEvent(RealtimeEvent.faction(RealtimeEvent.ETAT_VEHICULE, "SP",
                        engin + " → " + declenche.getLabel(),
                        Map.of("vehiculeId", engin.getId().toString(), "statut", declenche.getCode(),
                               "etat", engin.getEtat().getCode()),
                        actor()).withReference(inter.getCode()));
            }
            affectationService.bipCrew(engin, affectationService.departMessage(inter),
                    affectationService.departPayload(inter, engin), inter.getCode());
        }
    }
}

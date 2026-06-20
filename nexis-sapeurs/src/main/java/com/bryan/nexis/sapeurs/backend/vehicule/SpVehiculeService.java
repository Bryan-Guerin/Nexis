package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.core.realtime.RealtimeEvent;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeEngageableDto;
import com.bryan.nexis.sapeurs.backend.intervention.SpInterventionService;
import com.bryan.nexis.sapeurs.datamodel.SpNatureIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeStatut;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeTypePoste;
import com.bryan.nexis.sapeurs.datarepository.*;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
public class SpVehiculeService {

    private static final Logger log = LoggerFactory.getLogger(SpVehiculeService.class);

    private final SpVehiculeRepository            vehiculeRepo;
    private final SpVehiculeTypeRepository        typeRepo;
    private final SpVehiculeEtatRepository        etatRepo;
    private final SpVehiculeStatutRepository      statutRepo;
    private final SpCentreRepository              centreRepo;
    private final SpHopitalRepository             hopitalRepo;
    private final SpVehiculeAffectationRepository affectationRepo;
    private final SpVehiculeTypePosteRepository   posteRepo;
    private final SpCriRepository                 criRepo;
    private final SpInterventionService           interventionService;
    private final ApplicationEventPublisher<RealtimeEvent> events;
    private final SecurityService securityService;

    public SpVehiculeService(SpVehiculeRepository vehiculeRepo, SpVehiculeTypeRepository typeRepo,
                             SpVehiculeEtatRepository etatRepo, SpVehiculeStatutRepository statutRepo,
                             SpCentreRepository centreRepo, SpHopitalRepository hopitalRepo,
                             SpVehiculeAffectationRepository affectationRepo,
                             SpVehiculeTypePosteRepository posteRepo, SpCriRepository criRepo,
                             SpInterventionService interventionService,
                             ApplicationEventPublisher<RealtimeEvent> events, SecurityService securityService) {
        this.vehiculeRepo    = vehiculeRepo;
        this.typeRepo        = typeRepo;
        this.etatRepo        = etatRepo;
        this.statutRepo      = statutRepo;
        this.centreRepo      = centreRepo;
        this.hopitalRepo     = hopitalRepo;
        this.affectationRepo = affectationRepo;
        this.posteRepo       = posteRepo;
        this.criRepo         = criRepo;
        this.interventionService = interventionService;
        this.events          = events;
        this.securityService = securityService;
    }

    private String actor() { return securityService.username().orElse(null); }

    @Transactional
    public List<SpVehiculeDto> listAll() {
        return vehiculeRepo.findAll().stream().map(SpVehiculeDto::from).toList();
    }

    @Transactional
    public List<SpVehiculeDto> listByEtatCode(String code) {
        return vehiculeRepo.findByEtatCode(code).stream().map(SpVehiculeDto::from).toList();
    }

    /** Véhicules disponibles, avec leur état « armé » et les natures de leur type (proposition). */
    @Transactional
    public List<SpVehiculeEngageableDto> listEngageables() {
        return vehiculeRepo.findByEtatCode("DISPONIBLE").stream()
                .map(v -> new SpVehiculeEngageableDto(v.getId(), v.getLibelle(), v.getType().getCode(),
                        v.getType().getId(), estArme(v),
                        v.getType().getNatures().stream().map(SpNatureIntervention::getId).toList(),
                        v.getType().getNaturePrincipale() == null ? null : v.getType().getNaturePrincipale().getId()))
                .toList();
    }

    /**
     * Un véhicule est « armé » si chaque poste obligatoire de son type est occupé par un équipier
     * réellement disponible ; à défaut, dès 1 équipier. Un membre déjà engagé sur une intervention
     * en cours via un AUTRE véhicule ne compte pas (il est occupé ailleurs).
     */
    @Transactional
    public boolean estArme(SpVehicule v) {
        var crew = affectationRepo.findByVehiculeIdAndFinIsNull(v.getId());
        var occupes = interventionService.membresOccupesSurAutreIntervention(v.getId());
        var oblig = posteRepo.findByVehiculeTypeId(v.getType().getId()).stream()
                .filter(SpVehiculeTypePoste::isObligatoire).toList();
        if (oblig.isEmpty()) return crew.stream().anyMatch(a -> !occupes.contains(a.getMembre().getId()));
        return oblig.stream().allMatch(p -> crew.stream().anyMatch(a ->
                a.getPoste() != null && a.getPoste().getId().equals(p.getId())
                        && !occupes.contains(a.getMembre().getId())));
    }

    @Transactional
    public SpVehiculeDto create(UUID typeId, String libelle, String immatriculation,
                                UUID centreId, Integer capaciteEau, String notes) {
        var type = typeRepo.findById(typeId)
                .orElseThrow(() -> new NoSuchElementException("Type véhicule SP introuvable : " + typeId));
        var etat = etatRepo.findByCode("DISPONIBLE")
                .orElseThrow(() -> new IllegalStateException("État DISPONIBLE non configuré"));
        var statut = statutRepo.findByCode("DISPONIBLE")
                .orElseThrow(() -> new IllegalStateException("Statut DISPONIBLE non configuré"));
        var vehicule = new SpVehicule(type, libelle, etat, statut);
        vehicule.setImmatriculation(immatriculation);
        vehicule.setCapaciteEau(capaciteEau);
        vehicule.setNotes(notes);
        if (centreId != null) vehicule.setCentre(centreRepo.findById(centreId)
                .orElseThrow(() -> new NoSuchElementException("Centre introuvable : " + centreId)));
        return SpVehiculeDto.from(vehiculeRepo.save(vehicule));
    }

    /** Mise à jour partielle (null = inchangé). */
    @Transactional
    public SpVehiculeDto update(UUID id, String libelle, String immatriculation,
                                UUID centreId, Integer capaciteEau, String notes) {
        var vehicule = vehiculeRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Véhicule SP introuvable : " + id));
        if (libelle != null)         vehicule.setLibelle(libelle);
        if (immatriculation != null) vehicule.setImmatriculation(immatriculation);
        if (capaciteEau != null)     vehicule.setCapaciteEau(capaciteEau);
        if (notes != null)           vehicule.setNotes(notes);
        if (centreId != null)        vehicule.setCentre(centreRepo.findById(centreId)
                .orElseThrow(() -> new NoSuchElementException("Centre introuvable : " + centreId)));
        return SpVehiculeDto.from(vehiculeRepo.update(vehicule));
    }

    /**
     * Supprime un véhicule (admin). Réservé aux véhicules sans historique (ex. doublon créé par
     * erreur) : refusé s'il est engagé sur une intervention en cours, s'il a un historique
     * d'affectations, ou des comptes rendus d'intervention. Les vérifications d'inventaire sont
     * supprimées en cascade (FK ON DELETE CASCADE).
     */
    @Transactional
    public void delete(UUID id) {
        var vehicule = vehiculeRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Véhicule SP introuvable : " + id));
        interventionService.codeInterventionEnCours(id).ifPresent(code -> {
            throw new IllegalStateException("Suppression impossible : véhicule engagé sur l'intervention " + code + ".");
        });
        if (affectationRepo.countByVehiculeId(id) > 0) {
            throw new IllegalStateException("Suppression impossible : ce véhicule a un historique d'affectations. "
                    + "Seul un véhicule jamais armé (ex. doublon) peut être supprimé.");
        }
        if (criRepo.existsByVehiculeId(id)) {
            throw new IllegalStateException("Suppression impossible : ce véhicule a des comptes rendus d'intervention.");
        }
        String libelle = vehicule.getLibelle();
        log.info("Suppression du véhicule {} ({}) par {}", libelle, id, actor());
        vehiculeRepo.delete(vehicule);   // sp_verification supprimées en cascade
        // Trace d'audit (visible en main courante SP, filtrable par type/acteur).
        events.publishEvent(RealtimeEvent.faction("VEHICULE_SUPPRIME", "SP",
                "Véhicule supprimé : " + libelle,
                Map.of("vehiculeId", id.toString()), actor()));
    }

    /** Force l'état maître (action système : maintenance, inventaire, indisponibilité…). */
    @Transactional
    public SpVehiculeDto updateEtat(UUID id, UUID etatId) {
        var vehicule = vehiculeRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Véhicule SP introuvable : " + id));
        var etat = etatRepo.findById(etatId)
                .orElseThrow(() -> new NoSuchElementException("État SP introuvable : " + etatId));
        vehicule.setEtat(etat);

        // Retour à DISPONIBLE depuis un état système (maintenance, indispo…) : on réinitialise
        // le statut RP au statut par défaut — sinon la règle « transition avant uniquement »
        // bloque le retour (pas de rétrogradation). On efface aussi destination/position.
        if ("DISPONIBLE".equals(etat.getCode())) {
            statutRepo.findByParDefautTrue().ifPresent(vehicule::setStatut);
            vehicule.setHopitalDestination(null);
            vehicule.setPositionCoordonnees(null);
        }
        var updated = vehiculeRepo.update(vehicule);

        events.publishEvent(RealtimeEvent.faction(RealtimeEvent.ETAT_VEHICULE, "SP",
                vehicule + " → " + etat.getLabel(),
                Map.of("vehiculeId", id.toString(), "etat", etat.getCode()), actor()));

        return SpVehiculeDto.from(updated);
    }

    /**
     * Bascule le statut RP. Réservé à un équipier affecté (ou admin), transition avant uniquement,
     * et applique automatiquement l'état lié au statut.
     */
    @Transactional
    public SpVehiculeDto updateStatut(UUID id, UUID statutId, UUID hopitalId) {
        var vehicule = vehiculeRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Véhicule SP introuvable : " + id));
        var cible = statutRepo.findById(statutId)
                .orElseThrow(() -> new NoSuchElementException("Statut SP introuvable : " + statutId));

        // Seul un équipier affecté au véhicule (ou un admin) peut changer le statut
        if (!securityService.hasRole("ROLE_ADMIN_SP")) {
            var equipage = affectationRepo.findByVehiculeIdAndFinIsNull(id).stream()
                    .map(a -> a.getMembre().getUser().getUsername()).collect(Collectors.toSet());
            if (!equipage.contains(actor())) {
                throw new IllegalStateException("Seul un équipier affecté à ce véhicule peut changer son statut.");
            }
        }

        // Transition avant uniquement (impossible de revenir à un statut antérieur)
        SpVehiculeStatut courant = vehicule.getStatut();
        if (courant != null && cible.getPosition() < courant.getPosition()) {
            throw new IllegalStateException("Transition impossible : « " + courant.getLabel()
                    + " » ne peut pas revenir à « " + cible.getLabel() + " ».");
        }

        log.info("Statut véhicule {} : {} → {} (état {}) par {}", vehicule.getLibelle(),
                courant != null ? courant.getCode() : "?", cible.getCode(), cible.getEtat().getCode(), actor());
        vehicule.setStatut(cible);
        vehicule.setEtat(cible.getEtat());   // le statut pilote l'état maître
        vehicule.setLegDepart(java.time.Instant.now()); // nouveau trajet → réinitialise l'animation/ETA

        // Destination hôpital + position selon l'action carte du statut (souple : non bloquant).
        switch (cible.getActionCarte()) {
            case TRANSPORT_HOPITAL -> {
                if (hopitalId != null) {
                    vehicule.setHopitalDestination(hopitalRepo.findById(hopitalId)
                            .orElseThrow(() -> new NoSuchElementException("Hôpital introuvable : " + hopitalId)));
                }
                // hopitalId null : on conserve l'éventuelle destination déjà posée (warning côté front).
            }
            case SUR_PLACE -> {
                // Arrivé à l'hôpital : mémorise la position (origine d'un futur retour).
                if (vehicule.getHopitalDestination() != null) {
                    vehicule.setPositionCoordonnees(vehicule.getHopitalDestination().getCoordonnees());
                }
            }
            case RETOUR_CASERNE -> vehicule.setHopitalDestination(null);   // garde positionCoordonnees comme origine du retour
            default -> { /* AUCUNE / DEPANNEUR : inchangé */ }
        }
        if (cible.isClotureIntervention()) {   // libéré : on efface destination + position
            vehicule.setHopitalDestination(null);
            vehicule.setPositionCoordonnees(null);
        }

        var updated = vehiculeRepo.update(vehicule);

        var ev = RealtimeEvent.faction(RealtimeEvent.ETAT_VEHICULE, "SP",
                vehicule + " → " + cible.getLabel() + " (" + cible.getEtat().getLabel() + ")",
                Map.of("vehiculeId", id.toString(), "statut", cible.getCode(), "etat", cible.getEtat().getCode()), actor());
        // Rattache le changement à la main courante de l'intervention en cours, le cas échéant
        interventionService.codeInterventionEnCours(id).ifPresent(ev::withReference);
        events.publishEvent(ev);

        // Statut validant la clôture → clôturer les interventions dont tous les engins valident.
        // (« Disponible radio » non coché libère le véhicule sans fermer l'intervention.)
        if (cible.isClotureIntervention()) {
            interventionService.clotureSiEnginsValident(id);
        }

        return SpVehiculeDto.from(updated);
    }
}

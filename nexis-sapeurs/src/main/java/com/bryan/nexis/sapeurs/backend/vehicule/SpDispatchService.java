package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.sapeurs.backend.dto.SpDispatchDto;
import com.bryan.nexis.sapeurs.backend.dto.SpDispatchDto.SpDispatchMembreDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeEtatDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeStatutDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeTypeDto;
import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeAffectation;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeTypePoste;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypePosteRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVerificationRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
public class SpDispatchService {

    private final SpVehiculeRepository             vehiculeRepo;
    private final SpVehiculeAffectationRepository  affectationRepo;
    private final SpVehiculeTypePosteRepository    posteRepo;
    private final SpVerificationRepository         verifRepo;
    private final SpInterventionRepository         interventionRepo;

    public SpDispatchService(SpVehiculeRepository vehiculeRepo, SpVehiculeAffectationRepository affectationRepo,
                             SpVehiculeTypePosteRepository posteRepo, SpVerificationRepository verifRepo,
                             SpInterventionRepository interventionRepo) {
        this.vehiculeRepo     = vehiculeRepo;
        this.affectationRepo  = affectationRepo;
        this.posteRepo        = posteRepo;
        this.verifRepo        = verifRepo;
        this.interventionRepo = interventionRepo;
    }

    private static final Comparator<SpVehiculeAffectation> PAR_ORDRE_EQUIPAGE = Comparator
            // Catégorie de fonction (Chef d'agrès → Conducteur → Chef d'équipe → Équipier), puis ordre de poste.
            .comparingInt((SpVehiculeAffectation a) -> a.getPoste() != null ? a.getPoste().getFonction().getTypeFonction().ordinal() : Integer.MAX_VALUE)
            .thenComparingInt(a -> a.getPoste() != null ? a.getPoste().getOrdre() : Integer.MAX_VALUE);

    /**
     * Vue dispatch. Tout est préchargé en lot (aucun N+1) : véhicules, affectations actives,
     * postes par type, engins engagés, dernières vérifications — puis assemblé en mémoire.
     */
    @Transactional
    public List<SpDispatchDto> listDispatch() {
        var actives = affectationRepo.findByFinIsNull();
        Map<UUID, List<SpVehiculeAffectation>> affParVeh = actives.stream()
                .collect(Collectors.groupingBy(a -> a.getVehicule().getId()));
        Map<UUID, List<SpVehiculeTypePoste>> postesParType = posteRepo.findAll().stream()
                .collect(Collectors.groupingBy(p -> p.getVehiculeType().getId()));
        Set<UUID> enginsEngages = interventionRepo.findByFinIsNull().stream()
                .flatMap(i -> i.getEngins().stream()).map(SpVehicule::getId).collect(Collectors.toSet());
        Map<UUID, String> derniereVerif = new HashMap<>();
        for (Object[] r : verifRepo.findDernieresVerifs()) {
            derniereVerif.put((UUID) r[0], r[1] == null ? null : ((Instant) r[1]).toString());
        }

        return vehiculeRepo.findAll().stream().map(v -> {
            var crew = affParVeh.getOrDefault(v.getId(), List.of());
            // Membres occupés sur une AUTRE intervention en cours (engin engagé ≠ ce véhicule).
            Set<UUID> occupes = actives.stream()
                    .filter(a -> !a.getVehicule().getId().equals(v.getId()) && enginsEngages.contains(a.getVehicule().getId()))
                    .map(a -> a.getMembre().getId()).collect(Collectors.toSet());

            var oblig = postesParType.getOrDefault(v.getType().getId(), List.of()).stream()
                    .filter(SpVehiculeTypePoste::isObligatoire)
                    .sorted(Comparator.comparingInt(SpVehiculeTypePoste::getOrdre)).toList();
            List<String> manquants = oblig.stream()
                    .filter(p -> crew.stream().noneMatch(a -> a.getPoste() != null
                            && a.getPoste().getId().equals(p.getId()) && !occupes.contains(a.getMembre().getId())))
                    .map(p -> p.getFonction().getLabel()).toList();
            boolean arme = oblig.isEmpty()
                    ? crew.stream().anyMatch(a -> !occupes.contains(a.getMembre().getId()))
                    : manquants.isEmpty();

            var equipe = crew.stream().sorted(PAR_ORDRE_EQUIPAGE)
                    .map(a -> new SpDispatchMembreDto(
                            a.getMembre().getId(),
                            a.getMembre().getMatricule(),
                            a.getMembre().getUser().getUsername(),
                            a.getMembre().getGrade().getCode(),
                            a.getMembre().getGrade().getLabel(),
                            a.getMembre().getNomComplet(),
                            a.getPoste() != null ? a.getPoste().getId() : null,
                            a.getPoste() != null ? a.getPoste().getFonction().getLabel() : null))
                    .toList();

            return new SpDispatchDto(
                    v.getId(), v.getLibelle(), v.getImmatriculation(),
                    SpVehiculeTypeDto.from(v.getType()),
                    SpVehiculeEtatDto.from(v.getEtat()),
                    SpVehiculeStatutDto.from(v.getStatut()),
                    arme, equipe, manquants, derniereVerif.get(v.getId()),
                    v.getCentre() != null ? v.getCentre().getLabel() : null,
                    v.getCentre() != null ? v.getCentre().getCoordonnees() : null,
                    v.getHopitalDestination() != null ? v.getHopitalDestination().getCoordonnees() : null,
                    v.getPositionCoordonnees(),
                    v.getLegDepart() != null ? v.getLegDepart().toString() : null);
        }).toList();
    }
}

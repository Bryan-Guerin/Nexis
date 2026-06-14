package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.sapeurs.backend.dto.SpDispatchDto;
import com.bryan.nexis.sapeurs.backend.dto.SpDispatchDto.SpDispatchMembreDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeEtatDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeStatutDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeTypeDto;
import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeAffectation;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeTypePoste;
import com.bryan.nexis.sapeurs.backend.intervention.SpInterventionService;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypePosteRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Singleton
public class SpDispatchService {

    private final SpVehiculeRepository             vehiculeRepo;
    private final SpVehiculeAffectationRepository  affectationRepo;
    private final SpVehiculeTypePosteRepository    posteRepo;
    private final SpInterventionService            interventionService;

    public SpDispatchService(SpVehiculeRepository vehiculeRepo, SpVehiculeAffectationRepository affectationRepo,
                             SpVehiculeTypePosteRepository posteRepo, SpInterventionService interventionService) {
        this.vehiculeRepo    = vehiculeRepo;
        this.affectationRepo = affectationRepo;
        this.posteRepo       = posteRepo;
        this.interventionService = interventionService;
    }

    @Transactional
    public List<SpDispatchDto> listDispatch() {
        return vehiculeRepo.findAll().stream().map(v -> {
            var affectations = affectationRepo.findByVehiculeIdAndFinIsNull(v.getId());
            var equipe = affectations.stream()
                    // Ordre d'affichage = ordre des postes dans le type (postes sans ordre en fin).
                    .sorted(java.util.Comparator.comparingInt(
                            a -> a.getPoste() != null ? a.getPoste().getOrdre() : Integer.MAX_VALUE))
                    .map(a -> new SpDispatchMembreDto(
                            a.getMembre().getId(),
                            a.getMembre().getMatricule(),
                            a.getMembre().getUser().getUsername(),
                            a.getMembre().getGrade().getCode(),
                            a.getMembre().getGrade().getLabel(),
                            a.getMembre().getNomComplet(),
                            a.getPoste() != null ? a.getPoste().getId() : null,
                            a.getPoste() != null ? a.getPoste().getFonction().getLabel() : null
                    ))
                    .toList();
            return new SpDispatchDto(
                    v.getId(),
                    v.getLibelle(),
                    v.getImmatriculation(),
                    SpVehiculeTypeDto.from(v.getType()),
                    SpVehiculeEtatDto.from(v.getEtat()),
                    SpVehiculeStatutDto.from(v.getStatut()),
                    estArme(v, affectations),
                    equipe
            );
        }).toList();
    }

    private boolean estArme(SpVehicule v, List<SpVehiculeAffectation> crew) {
        var oblig = posteRepo.findByVehiculeTypeId(v.getType().getId()).stream()
                .filter(SpVehiculeTypePoste::isObligatoire).toList();
        // Un membre engagé sur une intervention via un autre véhicule n'est pas disponible ici.
        Set<UUID> occupes = interventionService.membresOccupesSurAutreIntervention(v.getId());
        if (oblig.isEmpty()) return crew.stream().anyMatch(a -> !occupes.contains(a.getMembre().getId()));
        return oblig.stream().allMatch(p -> crew.stream().anyMatch(a ->
                a.getPoste() != null && a.getPoste().getId().equals(p.getId())
                        && !occupes.contains(a.getMembre().getId())));
    }
}

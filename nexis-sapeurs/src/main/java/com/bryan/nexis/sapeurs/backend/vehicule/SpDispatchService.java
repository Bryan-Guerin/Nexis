package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.sapeurs.backend.dto.SpDispatchDto;
import com.bryan.nexis.sapeurs.backend.dto.SpDispatchDto.SpDispatchMembreDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeEtatDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeStatutDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeTypeDto;
import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeAffectation;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeTypePoste;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeAffectationRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypePosteRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class SpDispatchService {

    private final SpVehiculeRepository             vehiculeRepo;
    private final SpVehiculeAffectationRepository  affectationRepo;
    private final SpVehiculeTypePosteRepository    posteRepo;

    public SpDispatchService(SpVehiculeRepository vehiculeRepo, SpVehiculeAffectationRepository affectationRepo,
                             SpVehiculeTypePosteRepository posteRepo) {
        this.vehiculeRepo    = vehiculeRepo;
        this.affectationRepo = affectationRepo;
        this.posteRepo       = posteRepo;
    }

    @Transactional
    public List<SpDispatchDto> listDispatch() {
        return vehiculeRepo.findAll().stream().map(v -> {
            var affectations = affectationRepo.findByVehiculeIdAndFinIsNull(v.getId());
            var equipe = affectations.stream()
                    .map(a -> new SpDispatchMembreDto(
                            a.getMembre().getId(),
                            a.getMembre().getMatricule(),
                            a.getMembre().getUser().getUsername(),
                            a.getMembre().getGrade().getLabel(),
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
        if (oblig.isEmpty()) return !crew.isEmpty();
        return oblig.stream().allMatch(p -> crew.stream().anyMatch(a -> a.getPoste() != null && a.getPoste().getId().equals(p.getId())));
    }
}

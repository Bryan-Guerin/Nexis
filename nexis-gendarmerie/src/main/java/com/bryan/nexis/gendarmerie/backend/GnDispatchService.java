package com.bryan.nexis.gendarmerie.backend;

import com.bryan.nexis.gendarmerie.backend.dto.GnDispatchDto;
import com.bryan.nexis.gendarmerie.backend.dto.GnDispatchDto.GnDispatchMembreDto;
import com.bryan.nexis.gendarmerie.backend.dto.GnVehiculeEtatDto;
import com.bryan.nexis.gendarmerie.backend.dto.GnVehiculeTypeDto;
import com.bryan.nexis.gendarmerie.datarepository.GnVehiculeAffectationRepository;
import com.bryan.nexis.gendarmerie.datarepository.GnVehiculeRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class GnDispatchService {

    private final GnVehiculeRepository             vehiculeRepo;
    private final GnVehiculeAffectationRepository  affectationRepo;

    public GnDispatchService(GnVehiculeRepository vehiculeRepo, GnVehiculeAffectationRepository affectationRepo) {
        this.vehiculeRepo    = vehiculeRepo;
        this.affectationRepo = affectationRepo;
    }

    @Transactional
    public List<GnDispatchDto> listDispatch() {
        return vehiculeRepo.findAll().stream().map(v -> {
            var equipe = affectationRepo.findByVehiculeIdAndFinIsNull(v.getId()).stream()
                    .map(a -> new GnDispatchMembreDto(
                            a.getMembre().getId(),
                            a.getMembre().getMatricule(),
                            a.getMembre().getUser().getUsername(),
                            a.getMembre().getGrade().getLabel()
                    ))
                    .toList();
            return new GnDispatchDto(
                    v.getId(),
                    v.getLibelle(),
                    v.getImmatriculation(),
                    GnVehiculeTypeDto.from(v.getType()),
                    GnVehiculeEtatDto.from(v.getEtat()),
                    equipe
            );
        }).toList();
    }
}

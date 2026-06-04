package com.bryan.nexis.gendarmerie.backend;

import com.bryan.nexis.gendarmerie.backend.dto.GnVehiculeEtatDto;
import com.bryan.nexis.gendarmerie.datamodel.GnVehiculeEtat;
import com.bryan.nexis.gendarmerie.datarepository.GnVehiculeEtatRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class GnVehiculeEtatService {

    private final GnVehiculeEtatRepository repo;

    public GnVehiculeEtatService(GnVehiculeEtatRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<GnVehiculeEtatDto> listAll() {
        return repo.findAll().stream().map(GnVehiculeEtatDto::from).toList();
    }

    @Transactional
    public GnVehiculeEtatDto create(String code, String label, String couleur) {
        return GnVehiculeEtatDto.from(repo.save(new GnVehiculeEtat(code, label, couleur)));
    }
}

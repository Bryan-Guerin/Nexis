package com.bryan.nexis.gendarmerie.backend;

import com.bryan.nexis.gendarmerie.backend.dto.GnVehiculeTypeDto;
import com.bryan.nexis.gendarmerie.datamodel.GnVehiculeType;
import com.bryan.nexis.gendarmerie.datarepository.GnVehiculeTypeRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class GnVehiculeTypeService {

    private final GnVehiculeTypeRepository repo;

    public GnVehiculeTypeService(GnVehiculeTypeRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<GnVehiculeTypeDto> listAll() {
        return repo.findAll().stream().map(GnVehiculeTypeDto::from).toList();
    }

    @Transactional
    public GnVehiculeTypeDto create(String code, String label) {
        return GnVehiculeTypeDto.from(repo.save(new GnVehiculeType(code, label)));
    }
}

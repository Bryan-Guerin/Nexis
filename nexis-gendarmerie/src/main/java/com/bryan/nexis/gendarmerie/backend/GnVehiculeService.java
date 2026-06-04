package com.bryan.nexis.gendarmerie.backend;

import com.bryan.nexis.gendarmerie.backend.dto.GnVehiculeDto;
import com.bryan.nexis.gendarmerie.datamodel.GnVehicule;
import com.bryan.nexis.gendarmerie.datarepository.GnVehiculeEtatRepository;
import com.bryan.nexis.gendarmerie.datarepository.GnVehiculeRepository;
import com.bryan.nexis.gendarmerie.datarepository.GnVehiculeTypeRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class GnVehiculeService {

    private final GnVehiculeRepository     vehiculeRepo;
    private final GnVehiculeTypeRepository typeRepo;
    private final GnVehiculeEtatRepository etatRepo;

    public GnVehiculeService(GnVehiculeRepository vehiculeRepo, GnVehiculeTypeRepository typeRepo, GnVehiculeEtatRepository etatRepo) {
        this.vehiculeRepo = vehiculeRepo;
        this.typeRepo     = typeRepo;
        this.etatRepo     = etatRepo;
    }

    @Transactional
    public List<GnVehiculeDto> listAll() {
        return vehiculeRepo.findAll().stream().map(GnVehiculeDto::from).toList();
    }

    @Transactional
    public List<GnVehiculeDto> listByEtatCode(String code) {
        return vehiculeRepo.findByEtatCode(code).stream().map(GnVehiculeDto::from).toList();
    }

    @Transactional
    public GnVehiculeDto create(UUID typeId, String libelle, String immatriculation) {
        var type = typeRepo.findById(typeId)
                .orElseThrow(() -> new NoSuchElementException("Type véhicule GN introuvable : " + typeId));
        var etat = etatRepo.findByCode("DISPONIBLE")
                .orElseThrow(() -> new IllegalStateException("État DISPONIBLE non configuré"));
        var vehicule = new GnVehicule(type, libelle, etat);
        vehicule.setImmatriculation(immatriculation);
        return GnVehiculeDto.from(vehiculeRepo.save(vehicule));
    }

    @Transactional
    public GnVehiculeDto updateEtat(UUID id, UUID etatId) {
        var vehicule = vehiculeRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Véhicule GN introuvable : " + id));
        var etat = etatRepo.findById(etatId)
                .orElseThrow(() -> new NoSuchElementException("État GN introuvable : " + etatId));
        vehicule.setEtat(etat);
        return GnVehiculeDto.from(vehiculeRepo.update(vehicule));
    }
}

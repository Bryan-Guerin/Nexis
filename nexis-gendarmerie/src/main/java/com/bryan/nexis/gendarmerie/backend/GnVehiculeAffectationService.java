package com.bryan.nexis.gendarmerie.backend;

import com.bryan.nexis.gendarmerie.backend.dto.GnVehiculeAffectationDto;
import com.bryan.nexis.gendarmerie.datamodel.GnVehiculeAffectation;
import com.bryan.nexis.gendarmerie.datarepository.GnMembreRepository;
import com.bryan.nexis.gendarmerie.datarepository.GnVehiculeAffectationRepository;
import com.bryan.nexis.gendarmerie.datarepository.GnVehiculeRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class GnVehiculeAffectationService {

    private final GnVehiculeAffectationRepository affectationRepo;
    private final GnVehiculeRepository vehiculeRepo;
    private final GnMembreRepository membreRepo;

    public GnVehiculeAffectationService(GnVehiculeAffectationRepository affectationRepo,
                                        GnVehiculeRepository vehiculeRepo,
                                        GnMembreRepository membreRepo) {
        this.affectationRepo = affectationRepo;
        this.vehiculeRepo = vehiculeRepo;
        this.membreRepo = membreRepo;
    }

    @Transactional
    public List<GnVehiculeAffectationDto> findActives() {
        return affectationRepo.findByFinIsNull().stream().map(GnVehiculeAffectationDto::from).toList();
    }

    @Transactional
    public GnVehiculeAffectationDto affecter(UUID vehiculeId, UUID membreId, Instant debut) {
        var vehicule = vehiculeRepo.findById(vehiculeId)
                .orElseThrow(() -> new NoSuchElementException("Véhicule GN introuvable : " + vehiculeId));
        var membre = membreRepo.findById(membreId)
                .orElseThrow(() -> new NoSuchElementException("Membre GN introuvable : " + membreId));
        if (!affectationRepo.findByMembreIdAndFinIsNull(membreId).isEmpty()) {
            throw new IllegalStateException("Le membre " + membreId + " est déjà affecté à un véhicule");
        }
        return GnVehiculeAffectationDto.from(affectationRepo.save(new GnVehiculeAffectation(vehicule, membre, debut)));
    }

    @Transactional
    public GnVehiculeAffectationDto cloturer(UUID affectationId, Instant fin) {
        var affectation = affectationRepo.findById(affectationId)
                .orElseThrow(() -> new NoSuchElementException("Affectation GN introuvable : " + affectationId));
        affectation.setFin(fin);
        return GnVehiculeAffectationDto.from(affectationRepo.update(affectation));
    }
}

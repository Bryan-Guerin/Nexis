package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeEtatDto;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeEtat;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeEtatRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpVehiculeEtatService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final SpVehiculeEtatRepository repo;

    public SpVehiculeEtatService(SpVehiculeEtatRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<SpVehiculeEtatDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(SpVehiculeEtatDto::from).toList();
    }

    @Transactional
    public SpVehiculeEtatDto create(String code, String label, String couleur) {
        var etat = new SpVehiculeEtat(code, label, couleur);
        etat.setPosition((int) repo.count());
        return SpVehiculeEtatDto.from(repo.save(etat));
    }

    @Transactional
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var id = orderedIds.get(i);
            var etat = repo.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("État véhicule introuvable : " + id));
            etat.setPosition(i);
            repo.update(etat);
        }
    }
}

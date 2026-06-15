package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.backend.dto.SpNatureInterventionDto;
import com.bryan.nexis.sapeurs.datamodel.SpNatureIntervention;
import com.bryan.nexis.sapeurs.datarepository.SpInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpNatureInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypeRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpNatureInterventionService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final SpNatureInterventionRepository repo;
    private final SpInterventionRepository       interventionRepo;
    private final SpVehiculeTypeRepository        typeRepo;

    public SpNatureInterventionService(SpNatureInterventionRepository repo,
                                       SpInterventionRepository interventionRepo,
                                       SpVehiculeTypeRepository typeRepo) {
        this.repo             = repo;
        this.interventionRepo = interventionRepo;
        this.typeRepo         = typeRepo;
    }

    /**
     * Supprime une nature : refusée si une intervention l'utilise. Détache la nature des types de
     * véhicule (tags + nature principale), sans toucher aux véhicules ; promeut la nature restante
     * si le type n'en a plus qu'une.
     */
    @Transactional
    public void delete(UUID id) {
        var nature = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Nature introuvable : " + id));
        if (interventionRepo.existsByNatureId(id)) {
            throw new IllegalStateException("Impossible de supprimer : des interventions utilisent cette nature.");
        }
        for (var type : typeRepo.findAll()) {
            boolean modif = type.getNatures().removeIf(n -> n.getId().equals(id));
            if (type.getNaturePrincipale() != null && type.getNaturePrincipale().getId().equals(id)) {
                type.setNaturePrincipale(null);
                modif = true;
            }
            if (modif) {
                if (type.getNatures().size() == 1) type.setNaturePrincipale(type.getNatures().iterator().next());
                typeRepo.update(type);
            }
        }
        repo.delete(nature);
    }

    @Transactional
    public List<SpNatureInterventionDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(SpNatureInterventionDto::from).toList();
    }

    @Transactional
    public SpNatureInterventionDto create(String code, String label) {
        var n = new SpNatureIntervention(code, label);
        n.setPosition((int) repo.count());
        return SpNatureInterventionDto.from(repo.save(n));
    }

    @Transactional
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var id = orderedIds.get(i);
            var n = repo.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Nature introuvable : " + id));
            n.setPosition(i);
            repo.update(n);
        }
    }
}

package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.backend.dto.SpNatureInterventionDto;
import com.bryan.nexis.sapeurs.backend.effectif.SpBadgeService;
import com.bryan.nexis.sapeurs.datamodel.SpIcone;
import com.bryan.nexis.sapeurs.datamodel.SpNatureIntervention;
import com.bryan.nexis.sapeurs.datarepository.SpIconeRepository;
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
    private final SpBadgeService                  badgeService;
    private final SpIconeRepository               iconeRepo;

    public SpNatureInterventionService(SpNatureInterventionRepository repo,
                                       SpInterventionRepository interventionRepo,
                                       SpVehiculeTypeRepository typeRepo,
                                       SpBadgeService badgeService,
                                       SpIconeRepository iconeRepo) {
        this.repo             = repo;
        this.interventionRepo = interventionRepo;
        this.typeRepo         = typeRepo;
        this.badgeService     = badgeService;
        this.iconeRepo        = iconeRepo;
    }

    private SpIcone resolveIcone(UUID iconeImageId) {
        return iconeImageId != null ? iconeRepo.findById(iconeImageId).orElse(null) : null;
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
        badgeService.deleteForNature(id);   // retire les badges « ×1/×10/×50/×100 » de la nature
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
        var saved = repo.save(n);
        badgeService.createForNature(saved);   // génère les badges « ×1/×10/×50/×100 »
        return SpNatureInterventionDto.from(saved);
    }

    @Transactional
    public SpNatureInterventionDto setIcone(UUID id, String icone, UUID iconeImageId) {
        var n = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Nature introuvable : " + id));
        n.setIcone(icone == null || icone.isBlank() ? null : icone.trim());
        n.setIconeImage(resolveIcone(iconeImageId));
        return SpNatureInterventionDto.from(repo.update(n));
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

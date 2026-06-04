package com.bryan.nexis.sapeurs.backend.planning;

import com.bryan.nexis.core.backend.dto.PlanningStatutDto;
import com.bryan.nexis.core.datamodel.TypeService;
import com.bryan.nexis.sapeurs.datamodel.SpPlanningStatut;
import com.bryan.nexis.sapeurs.datarepository.SpPlanningStatutRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpPlanningStatutService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final SpPlanningStatutRepository repo;

    public SpPlanningStatutService(SpPlanningStatutRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<PlanningStatutDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(PlanningStatutDto::from).toList();
    }

    @Transactional
    public PlanningStatutDto create(String code, String label, String couleur, TypeService categorie) {
        var statut = new SpPlanningStatut(code, label, couleur, categorie);
        statut.setPosition((int) repo.count());
        return PlanningStatutDto.from(repo.save(statut));
    }

    @Transactional
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var id = orderedIds.get(i);
            var statut = repo.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Statut planning introuvable : " + id));
            statut.setPosition(i);
            repo.update(statut);
        }
    }
}

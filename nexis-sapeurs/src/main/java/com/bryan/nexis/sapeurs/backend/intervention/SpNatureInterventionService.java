package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.backend.dto.SpNatureInterventionDto;
import com.bryan.nexis.sapeurs.datamodel.SpNatureIntervention;
import com.bryan.nexis.sapeurs.datarepository.SpNatureInterventionRepository;
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

    public SpNatureInterventionService(SpNatureInterventionRepository repo) {
        this.repo = repo;
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

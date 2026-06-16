package com.bryan.nexis.sapeurs.backend.inventaire;

import com.bryan.nexis.sapeurs.backend.dto.SpHopitalDto;
import com.bryan.nexis.sapeurs.datamodel.SpHopital;
import com.bryan.nexis.sapeurs.datarepository.SpHopitalRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpHopitalService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final SpHopitalRepository repo;

    public SpHopitalService(SpHopitalRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<SpHopitalDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(SpHopitalDto::from).toList();
    }

    @Transactional
    public SpHopitalDto create(String code, String label) {
        var h = new SpHopital(code, label);
        h.setPosition((int) repo.count());
        return SpHopitalDto.from(repo.save(h));
    }

    @Transactional
    public SpHopitalDto setCoordonnees(UUID id, String coordonnees) {
        var h = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Hôpital introuvable : " + id));
        h.setCoordonnees(coordonnees == null || coordonnees.isBlank() ? null : coordonnees.trim());
        return SpHopitalDto.from(repo.update(h));
    }

    @Transactional
    public void delete(UUID id) {
        repo.deleteById(id);
    }

    @Transactional
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var id = orderedIds.get(i);
            var h = repo.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Hôpital introuvable : " + id));
            h.setPosition(i);
            repo.update(h);
        }
    }
}

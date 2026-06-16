package com.bryan.nexis.sapeurs.backend.inventaire;

import com.bryan.nexis.sapeurs.backend.dto.SpCentreDto;
import com.bryan.nexis.sapeurs.datamodel.SpCentre;
import com.bryan.nexis.sapeurs.datarepository.SpCentreRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpCentreService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final SpCentreRepository repo;

    public SpCentreService(SpCentreRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<SpCentreDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(SpCentreDto::from).toList();
    }

    @Transactional
    public SpCentreDto create(String code, String label) {
        var c = new SpCentre(code, label);
        c.setPosition((int) repo.count());
        return SpCentreDto.from(repo.save(c));
    }

    @Transactional
    public SpCentreDto setCoordonnees(UUID id, String coordonnees) {
        var c = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Centre introuvable : " + id));
        c.setCoordonnees(coordonnees == null || coordonnees.isBlank() ? null : coordonnees.trim());
        return SpCentreDto.from(repo.update(c));
    }

    @Transactional
    public void delete(UUID id) {
        repo.deleteById(id);
    }

    @Transactional
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var id = orderedIds.get(i);
            var c = repo.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Centre introuvable : " + id));
            c.setPosition(i);
            repo.update(c);
        }
    }
}

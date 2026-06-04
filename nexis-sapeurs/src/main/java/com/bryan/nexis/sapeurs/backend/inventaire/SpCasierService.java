package com.bryan.nexis.sapeurs.backend.inventaire;

import com.bryan.nexis.sapeurs.backend.dto.SpCasierDto;
import com.bryan.nexis.sapeurs.datamodel.SpCasier;
import com.bryan.nexis.sapeurs.datarepository.SpCasierRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpCasierService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final SpCasierRepository repo;

    public SpCasierService(SpCasierRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<SpCasierDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(SpCasierDto::from).toList();
    }

    @Transactional
    public SpCasierDto create(int numero) {
        var casier = new SpCasier(numero);
        casier.setPosition((int) repo.count());
        return SpCasierDto.from(repo.save(casier));
    }

    @Transactional
    public void delete(UUID id) {
        repo.deleteById(id);
    }

    @Transactional
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var id = orderedIds.get(i);
            var casier = repo.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Casier introuvable : " + id));
            casier.setPosition(i);
            repo.update(casier);
        }
    }
}

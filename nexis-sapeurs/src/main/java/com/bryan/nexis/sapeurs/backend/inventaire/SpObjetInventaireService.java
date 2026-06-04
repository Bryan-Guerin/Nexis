package com.bryan.nexis.sapeurs.backend.inventaire;

import com.bryan.nexis.sapeurs.backend.dto.SpObjetInventaireDto;
import com.bryan.nexis.sapeurs.datamodel.SpObjetInventaire;
import com.bryan.nexis.sapeurs.datarepository.SpObjetInventaireRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpObjetInventaireService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final SpObjetInventaireRepository repo;

    public SpObjetInventaireService(SpObjetInventaireRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<SpObjetInventaireDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(SpObjetInventaireDto::from).toList();
    }

    @Transactional
    public SpObjetInventaireDto create(String code, String label) {
        var o = new SpObjetInventaire(code, label);
        o.setPosition((int) repo.count());
        return SpObjetInventaireDto.from(repo.save(o));
    }

    @Transactional
    public void delete(UUID id) {
        repo.deleteById(id);
    }

    @Transactional
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var id = orderedIds.get(i);
            var o = repo.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Objet d'inventaire introuvable : " + id));
            o.setPosition(i);
            repo.update(o);
        }
    }
}

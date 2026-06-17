package com.bryan.nexis.sapeurs.backend.effectif;

import com.bryan.nexis.sapeurs.backend.dto.SpFonctionOrgaDto;
import com.bryan.nexis.sapeurs.datamodel.SpFonctionOrga;
import com.bryan.nexis.sapeurs.datarepository.SpFonctionOrgaRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Référentiel des fonctions d'organigramme (Chef de centre, RH, Chef de garde…).
 * Structurées en arbre par {@code parent}. Position = ordre parmi les frères.
 */
@Singleton
public class SpFonctionOrgaService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final SpFonctionOrgaRepository repo;

    public SpFonctionOrgaService(SpFonctionOrgaRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<SpFonctionOrgaDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(SpFonctionOrgaDto::from).toList();
    }

    @Transactional
    public SpFonctionOrgaDto create(String code, String label, UUID parentId, String icone) {
        var f = new SpFonctionOrga(code, label);
        if (parentId != null) {
            f.setParent(repo.findById(parentId).orElseThrow(
                    () -> new NoSuchElementException("Fonction parente introuvable : " + parentId)));
        }
        f.setIcone(icone);
        f.setPosition((int) repo.count());
        return SpFonctionOrgaDto.from(repo.save(f));
    }

    @Transactional
    public SpFonctionOrgaDto update(UUID id, String label, UUID parentId, String icone) {
        var f = repo.findById(id).orElseThrow(
                () -> new NoSuchElementException("Fonction introuvable : " + id));
        if (label != null) f.setLabel(label);
        if (parentId != null) {
            if (parentId.equals(id)) throw new IllegalArgumentException("Une fonction ne peut pas être son propre parent");
            f.setParent(repo.findById(parentId).orElseThrow(
                    () -> new NoSuchElementException("Fonction parente introuvable : " + parentId)));
        } else {
            f.setParent(null);
        }
        f.setIcone(icone);
        return SpFonctionOrgaDto.from(repo.update(f));
    }

    @Transactional
    public void delete(UUID id) {
        var f = repo.findById(id).orElseThrow(
                () -> new NoSuchElementException("Fonction introuvable : " + id));
        // Les enfants passent automatiquement à la racine (ON DELETE SET NULL) ;
        // les associations membre↔fonction sont supprimées en cascade (FK ON DELETE CASCADE).
        repo.delete(f);
    }

    @Transactional
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var id = orderedIds.get(i);
            var f = repo.findById(id).orElseThrow(
                    () -> new NoSuchElementException("Fonction introuvable : " + id));
            f.setPosition(i);
            repo.update(f);
        }
    }
}

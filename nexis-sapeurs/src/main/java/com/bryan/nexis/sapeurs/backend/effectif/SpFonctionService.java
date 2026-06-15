package com.bryan.nexis.sapeurs.backend.effectif;

import com.bryan.nexis.sapeurs.backend.dto.SpFonctionDto;
import com.bryan.nexis.sapeurs.datamodel.SpFonction;
import com.bryan.nexis.sapeurs.datarepository.SpFonctionRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpFonctionService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final SpFonctionRepository repo;
    private final List<SpFonctionDeletionListener> deletionListeners;

    public SpFonctionService(SpFonctionRepository repo, List<SpFonctionDeletionListener> deletionListeners) {
        this.repo = repo;
        this.deletionListeners = deletionListeners;
    }

    @Transactional
    public List<SpFonctionDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(SpFonctionDto::from).toList();
    }

    @Transactional
    public SpFonctionDto create(String code, String label) {
        var fonction = new SpFonction(code, label);
        fonction.setPosition((int) repo.count());
        return SpFonctionDto.from(repo.save(fonction));
    }

    /** Définit la catégorie de la fonction (ordre d'affichage de l'équipage au dispatch). */
    @Transactional
    public SpFonctionDto updateType(UUID id, String type) {
        var fonction = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Fonction introuvable : " + id));
        fonction.setTypeFonction(com.bryan.nexis.sapeurs.datamodel.TypeFonction.valueOf(type));
        return SpFonctionDto.from(repo.update(fonction));
    }

    /** Supprime une fonction : les dépendants (postes…) réagissent via les listeners. */
    @Transactional
    public void delete(UUID id) {
        var fonction = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Fonction introuvable : " + id));
        deletionListeners.forEach(l -> l.onDeleteFonction(id));
        repo.delete(fonction);
    }

    @Transactional
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var id = orderedIds.get(i);
            var fonction = repo.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Fonction introuvable : " + id));
            fonction.setPosition(i);
            repo.update(fonction);
        }
    }
}

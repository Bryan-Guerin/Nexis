package com.bryan.nexis.sapeurs.backend.effectif;

import com.bryan.nexis.sapeurs.backend.dto.SpGradeDto;
import com.bryan.nexis.sapeurs.datamodel.SpGrade;
import com.bryan.nexis.sapeurs.datamodel.SpMembre;
import com.bryan.nexis.sapeurs.datarepository.SpGradeRepository;
import com.bryan.nexis.sapeurs.datarepository.SpMembreRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpGradeService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final SpGradeRepository repo;
    private final SpMembreRepository membreRepo;

    public SpGradeService(SpGradeRepository repo, SpMembreRepository membreRepo) {
        this.repo = repo;
        this.membreRepo = membreRepo;
    }

    @Transactional
    public List<SpGradeDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(SpGradeDto::from).toList();
    }

    @Transactional
    public SpGradeDto create(String code, String label) {
        var grade = new SpGrade(code, label);
        grade.setPosition((int) repo.count());
        return SpGradeDto.from(repo.save(grade));
    }

    /** Définit les taux (garde + astreinte) d'un grade — null = inchangé. */
    @Transactional
    public SpGradeDto updateTaux(UUID id, java.math.BigDecimal tauxHoraire, java.math.BigDecimal tauxAstreinte) {
        var grade = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Grade SP introuvable : " + id));
        if (tauxHoraire != null)   grade.setTauxHoraire(tauxHoraire);
        if (tauxAstreinte != null) grade.setTauxAstreinte(tauxAstreinte);
        return SpGradeDto.from(repo.update(grade));
    }

    /**
     * Supprime un grade. Bloqué si au moins un effectif porte ce grade — le message liste
     * les effectifs concernés (matricule + nom).
     *
     * @throws IllegalStateException si le grade est affecté à un ou plusieurs effectifs.
     */
    @Transactional
    public void delete(UUID id) {
        var grade = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Grade SP introuvable : " + id));
        var membres = membreRepo.findByGradeId(id);
        if (!membres.isEmpty()) {
            var noms = membres.stream()
                    .map(SpGradeService::libelleMembre)
                    .sorted()
                    .toList();
            throw new IllegalStateException(
                    "Impossible de supprimer le grade « " + grade.getLabel() + " » : il est porté par "
                    + membres.size() + " effectif(s) — " + String.join(", ", noms)
                    + ". Changez leur grade avant de le supprimer.");
        }
        repo.delete(grade);
    }

    private static String libelleMembre(SpMembre m) {
        return m.getNomComplet() != null && !m.getNomComplet().isBlank()
                ? m.getMatricule() + " " + m.getNomComplet()
                : m.getMatricule();
    }

    @Transactional
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var id = orderedIds.get(i);
            var grade = repo.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Grade SP introuvable : " + id));
            grade.setPosition(i);
            repo.update(grade);
        }
    }
}

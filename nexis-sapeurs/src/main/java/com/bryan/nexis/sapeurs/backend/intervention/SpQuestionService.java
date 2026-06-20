package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.backend.dto.SpQuestionDto;
import com.bryan.nexis.sapeurs.datamodel.CibleQuestion;
import com.bryan.nexis.sapeurs.datamodel.SpNatureIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpQuestion;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeType;
import com.bryan.nexis.sapeurs.datamodel.TypeQuestion;
import com.bryan.nexis.sapeurs.datarepository.SpNatureInterventionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpQuestionRepository;
import com.bryan.nexis.sapeurs.datarepository.SpVehiculeTypeRepository;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Questionnaire guidé du dispatcher : configuration (CRUD admin) + liste pour le flux. */
@Singleton
public class SpQuestionService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));

    private final SpQuestionRepository           repo;
    private final SpNatureInterventionRepository natureRepo;
    private final SpVehiculeTypeRepository        typeRepo;

    public SpQuestionService(SpQuestionRepository repo, SpNatureInterventionRepository natureRepo,
                             SpVehiculeTypeRepository typeRepo) {
        this.repo       = repo;
        this.natureRepo = natureRepo;
        this.typeRepo   = typeRepo;
    }

    @Transactional
    public List<SpQuestionDto> listAll() {
        return repo.findAll(BY_POSITION).stream().map(SpQuestionDto::from).toList();
    }

    @Transactional
    public SpQuestionDto create(String libelle, String type, String cible, UUID natureSuggereeId,
                                UUID conditionQuestionId, Boolean conditionAttendue,
                                UUID recoVehiculeTypeId, Boolean recoParUnite) {
        if (libelle == null || libelle.isBlank()) throw new IllegalArgumentException("Libellé requis");
        var q = new SpQuestion(libelle.trim(), TypeQuestion.valueOf(type));
        q.setCible(cible != null ? CibleQuestion.valueOf(cible) : CibleQuestion.AUCUNE);
        q.setNatureSuggeree(resolveNature(natureSuggereeId));
        q.setConditionQuestion(resolveQuestion(conditionQuestionId, null));
        q.setConditionAttendue(conditionAttendue == null || conditionAttendue);
        q.setRecoVehiculeType(resolveType(recoVehiculeTypeId));
        q.setRecoParUnite(recoParUnite != null && recoParUnite);
        q.setPosition((int) repo.count());
        return SpQuestionDto.from(repo.save(q));
    }

    @Transactional
    public SpQuestionDto update(UUID id, String libelle, String type, String cible, UUID natureSuggereeId,
                                UUID conditionQuestionId, Boolean conditionAttendue,
                                UUID recoVehiculeTypeId, Boolean recoParUnite) {
        var q = repo.findById(id).orElseThrow(() -> new NoSuchElementException("Question introuvable : " + id));
        if (libelle != null && !libelle.isBlank()) q.setLibelle(libelle.trim());
        if (type != null)  q.setType(TypeQuestion.valueOf(type));
        q.setCible(cible != null ? CibleQuestion.valueOf(cible) : CibleQuestion.AUCUNE);
        q.setNatureSuggeree(resolveNature(natureSuggereeId));
        q.setConditionQuestion(resolveQuestion(conditionQuestionId, id));
        if (conditionAttendue != null) q.setConditionAttendue(conditionAttendue);
        q.setRecoVehiculeType(resolveType(recoVehiculeTypeId));
        q.setRecoParUnite(recoParUnite != null && recoParUnite);
        return SpQuestionDto.from(repo.update(q));
    }

    @Transactional
    public void delete(UUID id) {
        if (repo.countByConditionQuestionId(id) > 0) {
            throw new IllegalStateException("Suppression impossible : d'autres questions dépendent de celle-ci.");
        }
        repo.deleteById(id);
    }

    @Transactional
    public void reorder(List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            var q = repo.findById(orderedIds.get(i))
                    .orElseThrow(() -> new NoSuchElementException("Question introuvable"));
            q.setPosition(i);
            repo.update(q);
        }
    }

    private SpNatureIntervention resolveNature(UUID natureId) {
        return natureId != null ? natureRepo.findById(natureId).orElse(null) : null;
    }

    private SpVehiculeType resolveType(UUID typeId) {
        return typeId != null ? typeRepo.findById(typeId).orElse(null) : null;
    }

    /** Résout la question-condition (null si absente). {@code selfId} : interdit l'auto-référence. */
    private SpQuestion resolveQuestion(UUID conditionQuestionId, UUID selfId) {
        if (conditionQuestionId == null) return null;
        if (conditionQuestionId.equals(selfId)) {
            throw new IllegalArgumentException("Une question ne peut pas dépendre d'elle-même.");
        }
        return repo.findById(conditionQuestionId).orElse(null);
    }
}

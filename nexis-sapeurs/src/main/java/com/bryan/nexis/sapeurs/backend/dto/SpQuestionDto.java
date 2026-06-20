package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpQuestion;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Question du questionnaire guidé (config + flux dispatch). */
@Serdeable
public record SpQuestionDto(
        UUID id, String libelle, String type, int position, String cible,
        UUID natureSuggereeId, String natureSuggereeLabel,
        UUID conditionQuestionId, boolean conditionAttendue,
        UUID recoVehiculeTypeId, String recoVehiculeTypeLabel, boolean recoParUnite) {

    public static SpQuestionDto from(SpQuestion q) {
        return new SpQuestionDto(
                q.getId(), q.getLibelle(), q.getType().name(), q.getPosition(), q.getCible().name(),
                q.getNatureSuggeree() != null ? q.getNatureSuggeree().getId()    : null,
                q.getNatureSuggeree() != null ? q.getNatureSuggeree().getLabel() : null,
                q.getConditionQuestion() != null ? q.getConditionQuestion().getId() : null,
                q.isConditionAttendue(),
                q.getRecoVehiculeType() != null ? q.getRecoVehiculeType().getId()    : null,
                q.getRecoVehiculeType() != null ? q.getRecoVehiculeType().getLabel() : null,
                q.isRecoParUnite());
    }
}

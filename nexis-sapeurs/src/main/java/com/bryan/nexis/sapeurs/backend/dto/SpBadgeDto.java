package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpBadge;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record SpBadgeDto(
        UUID id, String code, String label, String icone, String description,
        String typeCondition, UUID natureId, String natureLabel, String typeFonction,
        int seuil, int xpReward, int position) {

    public static SpBadgeDto from(SpBadge b) {
        return new SpBadgeDto(
                b.getId(), b.getCode(), b.getLabel(), b.getIcone(), b.getDescription(),
                b.getTypeCondition().name(),
                b.getNature() != null ? b.getNature().getId()    : null,
                b.getNature() != null ? b.getNature().getLabel() : null,
                b.getTypeFonction() != null ? b.getTypeFonction().name() : null,
                b.getSeuil(), b.getXpReward(), b.getPosition());
    }
}

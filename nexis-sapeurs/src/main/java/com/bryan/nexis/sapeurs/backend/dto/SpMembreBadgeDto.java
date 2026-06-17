package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpMembreBadge;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/** Badge obtenu par un membre (vue publique : visible par tous). */
@Serdeable
public record SpMembreBadgeDto(
        UUID badgeId, String code, String label, String icone, String description,
        int xpReward, Instant obtenuLe, boolean decouvert) {

    public static SpMembreBadgeDto from(SpMembreBadge mb) {
        var b = mb.getBadge();
        return new SpMembreBadgeDto(
                b.getId(), b.getCode(), b.getLabel(), b.getIcone(), b.getDescription(),
                b.getXpReward(), mb.getObtenuLe(), mb.isDecouvert());
    }
}

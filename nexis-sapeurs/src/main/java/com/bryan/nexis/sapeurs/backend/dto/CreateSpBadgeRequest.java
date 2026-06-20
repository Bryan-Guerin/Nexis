package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record CreateSpBadgeRequest(
        String code, String label, String icone, UUID iconeImageId, String description,
        String typeCondition, UUID natureId, String typeFonction, UUID fonctionOrgaId,
        int seuil, int xpReward) {}

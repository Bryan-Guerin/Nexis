package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record UpdateSpBadgeRequest(
        String label, String icone, String description,
        String typeCondition, UUID natureId, String typeFonction, Integer seuil, Integer xpReward) {}

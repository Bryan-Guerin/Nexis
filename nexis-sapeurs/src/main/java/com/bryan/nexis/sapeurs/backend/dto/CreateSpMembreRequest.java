package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record CreateSpMembreRequest(
        UUID userId,
        UUID gradeId,
        String contrat,      // "SPP" ou "SPV"
        int numeroCasier,    // 0–30
        @Nullable String nomComplet,
        @Nullable String telephone
) {}

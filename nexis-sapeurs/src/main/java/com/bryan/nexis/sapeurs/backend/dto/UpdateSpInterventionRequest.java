package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Mise à jour des champs d'une intervention (null = inchangé). */
@Serdeable
public record UpdateSpInterventionRequest(
        @Nullable String motif,
        @Nullable UUID natureId,
        @Nullable String requerant,
        @Nullable String telephone,
        @Nullable String observation,
        @Nullable String commune,
        @Nullable String coordonnees
) {}

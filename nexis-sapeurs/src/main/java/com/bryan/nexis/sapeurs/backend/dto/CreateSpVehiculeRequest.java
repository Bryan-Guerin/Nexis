package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record CreateSpVehiculeRequest(
        UUID typeId,
        String libelle,
        @Nullable String immatriculation,
        @Nullable UUID centreId,
        @Nullable Integer capaciteEau,
        @Nullable String notes
) {}

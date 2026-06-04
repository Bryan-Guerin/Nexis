package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Mise à jour partielle d'un véhicule (champs null = inchangés). */
@Serdeable
public record UpdateSpVehiculeRequest(
        @Nullable String libelle,
        @Nullable String immatriculation,
        @Nullable UUID centreId,
        @Nullable Integer capaciteEau,
        @Nullable String notes
) {}

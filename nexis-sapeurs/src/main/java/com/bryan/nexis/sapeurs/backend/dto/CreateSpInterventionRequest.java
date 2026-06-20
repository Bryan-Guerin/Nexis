package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

@Serdeable
public record CreateSpInterventionRequest(
        String motif,
        UUID natureId,
        @Nullable String requerant,
        @Nullable String telephone,
        @Nullable String observation,
        @Nullable String commune,
        @Nullable String coordonnees,
        @Nullable Integer nbVictimes,
        boolean incendie,
        boolean vehiculeImplique,
        @Nullable List<UUID> vehiculeIds,
        /** Arme automatiquement les engins (équipage de garde) dans la transaction de création. */
        boolean armerAuto,
        /** Secours routier (AVP) : flag pilotant la couverture (lot SR). */
        boolean sr
) {}

package com.bryan.nexis.core.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CreateNotationRequest(
        String mois,
        int comportementDiscipline,
        int competencesTechniques,
        int aptitudePhysique,
        int initiativeAutonomie,
        int espritEquipe,
        int respectSecurite,
        @Nullable String observations,
        @Nullable String objectifs
) {}

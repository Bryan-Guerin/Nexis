package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/** Statistiques des interventions SP. */
@Serdeable
public record SpInterventionStatsDto(
        int total,
        int enCours,
        int cloturees,
        long dureeMoyenneMinutes,
        long totalVictimes,
        int nbIncendies,
        int nbAvecVehicule,
        List<NatureCount> parNature,
        List<MoisCount> parMois
) {
    @Serdeable
    public record NatureCount(String nature, long count) {}

    @Serdeable
    public record MoisCount(String mois, long count) {}
}

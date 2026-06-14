package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.core.backend.dto.JournalEntryDto;
import com.bryan.nexis.sapeurs.datamodel.SpIntervention;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Serdeable
public record SpInterventionDto(
        UUID id,
        String code,
        String motif,
        SpNatureInterventionDto nature,
        String requerant,
        String telephone,
        String observation,
        String commune,
        String coordonnees,
        Instant debut,
        Instant fin,
        boolean enCours,
        String creePar,
        Integer nbVictimes,
        boolean incendie,
        boolean vehiculeImplique,
        String renfortGn,
        String renfortVinci,
        List<SpEnginDto> engins,
        List<JournalEntryDto> dernieresLignes
) {
    public static SpInterventionDto from(SpIntervention i) {
        return from(i, List.of());
    }

    public static SpInterventionDto from(SpIntervention i, List<JournalEntryDto> dernieresLignes) {
        return new SpInterventionDto(
                i.getId(),
                i.getCode(),
                i.getMotif(),
                SpNatureInterventionDto.from(i.getNature()),
                i.getRequerant(),
                i.getTelephone(),
                i.getObservation(),
                i.getCommune(),
                i.getCoordonnees(),
                i.getDebut(),
                i.getFin(),
                i.getFin() == null,
                i.getCreePar(),
                i.getNbVictimes(),
                i.isIncendie(),
                i.isVehiculeImplique(),
                i.getRenfortGn(),
                i.getRenfortVinci(),
                i.getEngins().stream().map(SpEnginDto::from).toList(),
                dernieresLignes
        );
    }
}

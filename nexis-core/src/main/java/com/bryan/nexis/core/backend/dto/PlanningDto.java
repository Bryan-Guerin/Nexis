package com.bryan.nexis.core.backend.dto;

import com.bryan.nexis.core.datamodel.AbstractPlanning;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/** Projection homogène d'une plage de planning, toutes factions confondues. */
@Serdeable
public record PlanningDto(
        UUID id,
        UUID membreId,
        String membreMatricule,
        String membreUsername,
        Instant debut,
        Instant fin,
        PlanningStatutDto statut,
        String notes
) {
    public static PlanningDto from(AbstractPlanning p) {
        return new PlanningDto(
                p.getId(),
                p.getMembreId(),
                p.getMembreMatricule(),
                p.getMembreUsername(),
                p.getDebut(),
                p.getFin(),
                PlanningStatutDto.from(p.getStatutView()),
                p.getNotes()
        );
    }
}

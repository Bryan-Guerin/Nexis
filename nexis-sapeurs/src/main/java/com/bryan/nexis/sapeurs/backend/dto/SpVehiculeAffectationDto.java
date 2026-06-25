package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpVehiculeAffectation;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record SpVehiculeAffectationDto(UUID id, UUID vehiculeId, UUID membreId, UUID posteId,
                                       String fonctionCode, String fonctionLabel,
                                       Instant debut, Instant fin,
                                       boolean forcee, String forcePar) {

    public static SpVehiculeAffectationDto from(SpVehiculeAffectation a) {
        return new SpVehiculeAffectationDto(
                a.getId(),
                a.getVehicule().getId(),
                a.getMembre().getId(),
                a.getPoste() != null ? a.getPoste().getId() : null,
                a.getPoste() != null ? a.getPoste().getFonction().getCode() : null,
                a.getPoste() != null ? a.getPoste().getFonction().getLabel() : null,
                a.getDebut(),
                a.getFin(),
                a.isForcee(),
                a.getForcePar()
        );
    }
}

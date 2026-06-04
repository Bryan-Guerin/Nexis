package com.bryan.nexis.gendarmerie.backend.dto;

import com.bryan.nexis.gendarmerie.datamodel.GnVehiculeAffectation;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record GnVehiculeAffectationDto(UUID id, UUID vehiculeId, UUID membreId, Instant debut, Instant fin) {

    public static GnVehiculeAffectationDto from(GnVehiculeAffectation a) {
        return new GnVehiculeAffectationDto(a.getId(), a.getVehicule().getId(), a.getMembre().getId(), a.getDebut(), a.getFin());
    }
}

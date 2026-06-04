package com.bryan.nexis.gendarmerie.backend.dto;

import com.bryan.nexis.gendarmerie.datamodel.GnVehiculeType;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record GnVehiculeTypeDto(UUID id, String code, String label) {

    public static GnVehiculeTypeDto from(GnVehiculeType t) {
        return new GnVehiculeTypeDto(t.getId(), t.getCode(), t.getLabel());
    }
}

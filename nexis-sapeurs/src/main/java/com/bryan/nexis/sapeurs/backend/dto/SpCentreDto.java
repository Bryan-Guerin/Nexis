package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpCentre;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record SpCentreDto(UUID id, String code, String label, int position, String coordonnees) {
    public static SpCentreDto from(SpCentre c) {
        return new SpCentreDto(c.getId(), c.getCode(), c.getLabel(), c.getPosition(), c.getCoordonnees());
    }
}

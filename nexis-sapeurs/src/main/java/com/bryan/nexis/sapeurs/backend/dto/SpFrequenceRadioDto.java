package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpFrequenceRadio;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record SpFrequenceRadioDto(UUID id, String description, String frequence, int position) {
    public static SpFrequenceRadioDto from(SpFrequenceRadio f) {
        return new SpFrequenceRadioDto(f.getId(), f.getDescription(), f.getFrequence(), f.getPosition());
    }
}

package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpFonction;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record SpFonctionDto(UUID id, String code, String label, int position) {

    public static SpFonctionDto from(SpFonction f) {
        return new SpFonctionDto(f.getId(), f.getCode(), f.getLabel(), f.getPosition());
    }
}

package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpCasier;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record SpCasierDto(UUID id, int numero, int position) {

    public static SpCasierDto from(SpCasier c) {
        return new SpCasierDto(c.getId(), c.getNumero(), c.getPosition());
    }
}

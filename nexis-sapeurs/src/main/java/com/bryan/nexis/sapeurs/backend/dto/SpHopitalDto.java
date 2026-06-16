package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpHopital;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record SpHopitalDto(UUID id, String code, String label, int position, String coordonnees) {
    public static SpHopitalDto from(SpHopital h) {
        return new SpHopitalDto(h.getId(), h.getCode(), h.getLabel(), h.getPosition(), h.getCoordonnees());
    }
}

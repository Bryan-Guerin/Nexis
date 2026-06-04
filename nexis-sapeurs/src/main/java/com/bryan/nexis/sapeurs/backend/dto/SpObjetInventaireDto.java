package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpObjetInventaire;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record SpObjetInventaireDto(UUID id, String code, String label, int position) {
    public static SpObjetInventaireDto from(SpObjetInventaire o) {
        return new SpObjetInventaireDto(o.getId(), o.getCode(), o.getLabel(), o.getPosition());
    }
}

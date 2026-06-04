package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpVehiculeEtat;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** État maître du véhicule (système). */
@Serdeable
public record SpVehiculeEtatDto(UUID id, String code, String label, String couleur, int position) {

    public static SpVehiculeEtatDto from(SpVehiculeEtat e) {
        return new SpVehiculeEtatDto(e.getId(), e.getCode(), e.getLabel(), e.getCouleur(), e.getPosition());
    }
}

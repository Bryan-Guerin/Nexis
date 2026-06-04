package com.bryan.nexis.gendarmerie.backend.dto;

import com.bryan.nexis.gendarmerie.datamodel.GnVehiculeEtat;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record GnVehiculeEtatDto(UUID id, String code, String label, String couleur) {

    public static GnVehiculeEtatDto from(GnVehiculeEtat e) {
        return new GnVehiculeEtatDto(e.getId(), e.getCode(), e.getLabel(), e.getCouleur());
    }
}

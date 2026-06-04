package com.bryan.nexis.gendarmerie.backend.dto;

import com.bryan.nexis.gendarmerie.datamodel.GnVehicule;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record GnVehiculeDto(UUID id, GnVehiculeTypeDto type, String immatriculation, String libelle, GnVehiculeEtatDto etat, String notes) {

    public static GnVehiculeDto from(GnVehicule v) {
        return new GnVehiculeDto(
                v.getId(),
                GnVehiculeTypeDto.from(v.getType()),
                v.getImmatriculation(),
                v.getLibelle(),
                GnVehiculeEtatDto.from(v.getEtat()),
                v.getNotes()
        );
    }
}

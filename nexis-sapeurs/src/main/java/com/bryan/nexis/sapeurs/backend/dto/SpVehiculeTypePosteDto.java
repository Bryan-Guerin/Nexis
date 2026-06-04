package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpVehiculeTypePoste;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record SpVehiculeTypePosteDto(UUID id, UUID vehiculeTypeId, UUID fonctionId, String fonctionCode,
                                     String fonctionLabel, short nbPlaces, boolean obligatoire) {

    public static SpVehiculeTypePosteDto from(SpVehiculeTypePoste p) {
        return new SpVehiculeTypePosteDto(
                p.getId(),
                p.getVehiculeType().getId(),
                p.getFonction().getId(),
                p.getFonction().getCode(),
                p.getFonction().getLabel(),
                p.getNbPlaces(),
                p.isObligatoire()
        );
    }
}

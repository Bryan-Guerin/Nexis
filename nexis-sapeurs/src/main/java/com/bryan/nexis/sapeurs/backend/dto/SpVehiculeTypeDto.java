package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpNatureIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeType;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

@Serdeable
public record SpVehiculeTypeDto(UUID id, String code, String label, List<UUID> natureIds) {

    public static SpVehiculeTypeDto from(SpVehiculeType t) {
        return new SpVehiculeTypeDto(t.getId(), t.getCode(), t.getLabel(),
                t.getNatures().stream().map(SpNatureIntervention::getId).toList());
    }
}

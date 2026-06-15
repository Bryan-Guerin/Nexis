package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpTemplateDepart;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Ligne d'un lot de départ : un type de véhicule + quantité, pour une nature. */
@Serdeable
public record SpTemplateDepartDto(UUID id, UUID natureId, UUID vehiculeTypeId,
                                  String typeCode, String typeLabel, int quantite) {

    public static SpTemplateDepartDto from(SpTemplateDepart t) {
        return new SpTemplateDepartDto(t.getId(), t.getNature().getId(), t.getVehiculeType().getId(),
                t.getVehiculeType().getCode(), t.getVehiculeType().getLabel(), t.getQuantite());
    }
}

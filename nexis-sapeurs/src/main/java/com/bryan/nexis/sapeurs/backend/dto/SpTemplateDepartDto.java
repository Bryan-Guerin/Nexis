package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpTemplateDepart;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Ligne d'un lot de départ : un type de véhicule + quantité, pour une nature OU un flag déclencheur. */
@Serdeable
public record SpTemplateDepartDto(UUID id, UUID natureId, String declencheurFlag, UUID vehiculeTypeId,
                                  String typeCode, String typeLabel, int quantite,
                                  String description, UUID iconeImageId) {

    public static SpTemplateDepartDto from(SpTemplateDepart t) {
        return new SpTemplateDepartDto(t.getId(),
                t.getNature() != null ? t.getNature().getId() : null,
                t.getDeclencheurFlag() != null ? t.getDeclencheurFlag().name() : null,
                t.getVehiculeType().getId(), t.getVehiculeType().getCode(), t.getVehiculeType().getLabel(),
                t.getQuantite(), t.getDescription(),
                t.getIconeImage() != null ? t.getIconeImage().getId() : null);
    }
}

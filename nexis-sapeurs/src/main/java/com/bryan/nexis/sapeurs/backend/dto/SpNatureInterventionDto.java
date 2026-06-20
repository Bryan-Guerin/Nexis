package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpNatureIntervention;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record SpNatureInterventionDto(UUID id, String code, String label, int position,
                                      String icone, UUID iconeImageId) {
    public static SpNatureInterventionDto from(SpNatureIntervention n) {
        return new SpNatureInterventionDto(n.getId(), n.getCode(), n.getLabel(), n.getPosition(),
                n.getIcone(), n.getIconeImage() != null ? n.getIconeImage().getId() : null);
    }
}

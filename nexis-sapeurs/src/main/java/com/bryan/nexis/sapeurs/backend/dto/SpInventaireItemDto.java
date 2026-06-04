package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpInventaireItem;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record SpInventaireItemDto(UUID id, UUID vehiculeTypeId, UUID objetId,
                                  String objetCode, String objetLabel, int quantite, int position) {
    public static SpInventaireItemDto from(SpInventaireItem i) {
        return new SpInventaireItemDto(i.getId(), i.getVehiculeType().getId(), i.getObjet().getId(),
                i.getObjet().getCode(), i.getObjet().getLabel(), i.getQuantite(), i.getPosition());
    }
}

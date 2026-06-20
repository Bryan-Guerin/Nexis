package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpVehiculeAffectation;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Affectation active de l'utilisateur courant — pour le chip « mon affectation ». */
@Serdeable
public record SpMonAffectationDto(UUID vehiculeId, String vehiculeLibelle, String typeCode,
                                  String typeIcone, UUID typeIconeImageId, String fonctionLabel, String fonctionCode) {

    public static SpMonAffectationDto from(SpVehiculeAffectation a) {
        var v = a.getVehicule();
        var f = a.getPoste() != null ? a.getPoste().getFonction() : null;
        return new SpMonAffectationDto(
                v.getId(), v.getLibelle(), v.getType().getCode(), v.getType().getIcone(),
                v.getType().getIconeImage() != null ? v.getType().getIconeImage().getId() : null,
                f != null ? f.getLabel() : null, f != null ? f.getCode() : null);
    }
}

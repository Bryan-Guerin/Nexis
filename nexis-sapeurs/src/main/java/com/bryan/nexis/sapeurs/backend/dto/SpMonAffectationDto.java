package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpVehiculeAffectation;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Affectation active de l'utilisateur courant — pour le chip « mon affectation ». */
@Serdeable
public record SpMonAffectationDto(UUID vehiculeId, String vehiculeLibelle, String typeCode,
                                  String typeIcone, String fonctionLabel) {

    public static SpMonAffectationDto from(SpVehiculeAffectation a) {
        var v = a.getVehicule();
        return new SpMonAffectationDto(
                v.getId(), v.getLibelle(), v.getType().getCode(), v.getType().getIcone(),
                a.getPoste() != null ? a.getPoste().getFonction().getLabel() : null);
    }
}

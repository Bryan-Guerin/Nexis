package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpVehiculeStatut;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Statut RP du véhicule, avec l'état maître qu'il applique. */
@Serdeable
public record SpVehiculeStatutDto(UUID id, String code, String label, String couleur, int position,
                                  boolean parDefaut, boolean clotureIntervention, String actionCarte,
                                  SpVehiculeEtatDto etat) {

    public static SpVehiculeStatutDto from(SpVehiculeStatut s) {
        return new SpVehiculeStatutDto(s.getId(), s.getCode(), s.getLabel(), s.getCouleur(), s.getPosition(),
                s.isParDefaut(), s.isClotureIntervention(), s.getActionCarte().name(), SpVehiculeEtatDto.from(s.getEtat()));
    }
}

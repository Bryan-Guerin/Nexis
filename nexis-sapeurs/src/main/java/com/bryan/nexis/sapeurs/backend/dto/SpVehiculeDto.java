package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record SpVehiculeDto(
        UUID id,
        SpVehiculeTypeDto type,
        String immatriculation,
        String libelle,
        SpVehiculeEtatDto etat,
        SpVehiculeStatutDto statut,
        SpCentreDto centre,
        Integer capaciteEau,
        String notes
) {
    public static SpVehiculeDto from(SpVehicule v) {
        return new SpVehiculeDto(
                v.getId(),
                SpVehiculeTypeDto.from(v.getType()),
                v.getImmatriculation(),
                v.getLibelle(),
                SpVehiculeEtatDto.from(v.getEtat()),
                SpVehiculeStatutDto.from(v.getStatut()),
                v.getCentre() != null ? SpCentreDto.from(v.getCentre()) : null,
                v.getCapaciteEau(),
                v.getNotes()
        );
    }
}

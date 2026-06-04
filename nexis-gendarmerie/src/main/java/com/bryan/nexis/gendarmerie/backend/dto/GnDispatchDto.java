package com.bryan.nexis.gendarmerie.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

@Serdeable
public record GnDispatchDto(
        UUID id,
        String libelle,
        String immatriculation,
        GnVehiculeTypeDto type,
        GnVehiculeEtatDto etat,
        List<GnDispatchMembreDto> equipe
) {
    @Serdeable
    public record GnDispatchMembreDto(UUID membreId, String matricule, String username, String grade) {}
}

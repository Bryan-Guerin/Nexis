package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

@Serdeable
public record SpDispatchDto(
        UUID id,
        String libelle,
        String immatriculation,
        SpVehiculeTypeDto type,
        SpVehiculeEtatDto etat,
        SpVehiculeStatutDto statut,
        boolean arme,
        List<SpDispatchMembreDto> equipe
) {
    @Serdeable
    public record SpDispatchMembreDto(UUID membreId, String matricule, String username, String gradeCode,
                                      String grade, String nomComplet, UUID posteId, String fonctionLabel) {}
}

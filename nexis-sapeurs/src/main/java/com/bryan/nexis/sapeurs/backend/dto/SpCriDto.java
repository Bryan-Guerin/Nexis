package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpCri;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record SpCriDto(UUID id, UUID vehiculeId, String vehiculeLibelle, String contenu, String statut,
                       String soumisPar, Instant soumisLe, String validePar, Instant valideLe) {
    public static SpCriDto from(SpCri c) {
        return new SpCriDto(c.getId(), c.getVehicule().getId(), c.getVehicule().getLibelle(),
                c.getContenu(), c.getStatut(), c.getSoumisPar(), c.getSoumisLe(), c.getValidePar(), c.getValideLe());
    }
}

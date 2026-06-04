package com.bryan.nexis.gendarmerie.backend.dto;

import com.bryan.nexis.gendarmerie.datamodel.GnMembre;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record GnMembreDto(UUID id, UUID userId, String username, UUID gradeId, String grade, String matricule, boolean actif) {

    public static GnMembreDto from(GnMembre m) {
        return new GnMembreDto(
                m.getId(),
                m.getUser().getId(),
                m.getUser().getUsername(),
                m.getGrade().getId(),
                m.getGrade().getLabel(),
                m.getMatricule(),
                m.isActif()
        );
    }
}

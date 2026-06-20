package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpVictime;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/** Victime d'une intervention (unité du bilan SAP). */
@Serdeable
public record SpVictimeDto(UUID id, int numero, String libelle, String nom, String prenom,
                           String sexe, Instant creeLe) {

    public static SpVictimeDto from(SpVictime v) {
        return new SpVictimeDto(v.getId(), v.getNumero(), v.getLibelle(), v.getNom(), v.getPrenom(),
                v.getSexe() != null ? v.getSexe().name() : null, v.getCreeLe());
    }
}

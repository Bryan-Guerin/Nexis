package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpIcone;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Métadonnées d'une icône (sans le binaire) — pour la liste/picker admin. */
@Serdeable
public record SpIconeDto(UUID id, String nom, String contentType, long taille) {
    public static SpIconeDto from(SpIcone i) {
        return new SpIconeDto(i.getId(), i.getNom(), i.getContentType(), i.getTaille());
    }
}

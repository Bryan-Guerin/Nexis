package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpDocument;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/** Métadonnées d'un document (sans le contenu binaire). Sert aussi de projection en lecture. */
@Serdeable
public record SpDocumentDto(UUID id, String nom, String contentType, long taille, Instant creeLe, String creePar) {

    public static SpDocumentDto from(SpDocument d) {
        return new SpDocumentDto(d.getId(), d.getNom(), d.getContentType(), d.getTaille(), d.getCreeLe(), d.getCreePar());
    }
}

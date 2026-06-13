package com.bryan.nexis.core.backend.dto;

import com.bryan.nexis.core.datamodel.JournalEvenement;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * Entrée de journal / main courante.
 * {@code acteurNom} : nom lisible de l'acteur (résolu par faction, ex. nom RP du pompier) ;
 * null si non résolu — le front retombe alors sur {@code acteurUsername}.
 */
@Serdeable
public record JournalEntryDto(UUID id, String type, String faction, String acteurUsername,
                              String acteurNom, String message, String reference, Instant creeLe) {

    public static JournalEntryDto from(JournalEvenement j) {
        return new JournalEntryDto(j.getId(), j.getType(), j.getFaction(), j.getActeurUsername(),
                null, j.getMessage(), j.getReference(), j.getCreeLe());
    }

    /** Copie avec le nom d'acteur résolu. */
    public JournalEntryDto withActeurNom(String nom) {
        return new JournalEntryDto(id, type, faction, acteurUsername, nom, message, reference, creeLe);
    }
}

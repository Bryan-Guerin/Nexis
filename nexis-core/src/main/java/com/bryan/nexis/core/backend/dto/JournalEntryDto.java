package com.bryan.nexis.core.backend.dto;

import com.bryan.nexis.core.datamodel.JournalEvenement;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record JournalEntryDto(UUID id, String type, String faction, String acteurUsername, String message, String reference, Instant creeLe) {

    public static JournalEntryDto from(JournalEvenement j) {
        return new JournalEntryDto(j.getId(), j.getType(), j.getFaction(), j.getActeurUsername(), j.getMessage(), j.getReference(), j.getCreeLe());
    }
}

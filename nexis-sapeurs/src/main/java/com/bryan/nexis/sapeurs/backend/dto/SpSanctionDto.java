package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpSanction;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Serdeable
public record SpSanctionDto(UUID id, UUID membreId, String type, String motif,
                            LocalDate dateSanction, String creePar, Instant creeLe) {
    public static SpSanctionDto from(SpSanction s) {
        return new SpSanctionDto(s.getId(), s.getMembre().getId(), s.getType(), s.getMotif(),
                s.getDateSanction(), s.getCreePar(), s.getCreeLe());
    }
}

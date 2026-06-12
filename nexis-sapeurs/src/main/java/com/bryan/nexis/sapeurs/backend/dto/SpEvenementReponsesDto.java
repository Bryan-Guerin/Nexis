package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

/** Bilan des réponses d'un événement : présents et absents déclarés (hors non-répondants). */
@Serdeable
public record SpEvenementReponsesDto(List<MembrePresence> presents, List<MembrePresence> absents) {

    @Serdeable
    public record MembrePresence(UUID membreId, String matricule, String gradeCode, String nomComplet, String username) {}
}

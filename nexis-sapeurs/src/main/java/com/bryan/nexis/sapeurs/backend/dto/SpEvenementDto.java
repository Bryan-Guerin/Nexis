package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement vu côté liste / tableau de bord.
 * {@code maPresence} = réponse de l'utilisateur courant (true=présent, false=absent, null=pas répondu).
 */
@Serdeable
public record SpEvenementDto(
        UUID id,
        String titre,
        String texte,
        Instant date,
        int nbPresents,
        int nbAbsents,
        @Nullable Boolean maPresence
) {}

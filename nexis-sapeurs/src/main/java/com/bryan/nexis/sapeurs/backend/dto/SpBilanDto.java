package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/** Enveloppe d'un bilan + son contenu déjà parsé (JSON → objet). */
@Serdeable
public record SpBilanDto(UUID id, String famille, @Nullable UUID victimeId, Object contenu,
                         String auteur, Instant creeLe, @Nullable Instant majLe) {}

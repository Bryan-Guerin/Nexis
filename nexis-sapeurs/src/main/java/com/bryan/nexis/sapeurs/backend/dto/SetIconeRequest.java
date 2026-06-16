package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

/** Icône (emoji) — null/vide pour effacer. */
@Serdeable
public record SetIconeRequest(String icone) {}

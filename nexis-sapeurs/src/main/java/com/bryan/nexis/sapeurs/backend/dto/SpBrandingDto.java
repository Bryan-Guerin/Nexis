package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Branding de l'instance exposé au front. {@code logoIconeId} null = pas de logo en base. */
@Serdeable
public record SpBrandingDto(UUID logoIconeId) {}

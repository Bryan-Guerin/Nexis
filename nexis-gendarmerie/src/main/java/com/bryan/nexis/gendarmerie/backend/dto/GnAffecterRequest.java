package com.bryan.nexis.gendarmerie.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record GnAffecterRequest(UUID vehiculeId, UUID membreId, Instant debut) {}

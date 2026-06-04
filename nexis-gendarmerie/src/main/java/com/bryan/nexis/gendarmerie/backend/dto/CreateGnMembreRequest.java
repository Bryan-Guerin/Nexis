package com.bryan.nexis.gendarmerie.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record CreateGnMembreRequest(UUID userId, UUID gradeId, String matricule) {}

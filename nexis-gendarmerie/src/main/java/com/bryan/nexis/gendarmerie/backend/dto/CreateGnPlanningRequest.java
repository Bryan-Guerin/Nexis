package com.bryan.nexis.gendarmerie.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record CreateGnPlanningRequest(Instant debut, Instant fin, UUID statutId, String notes) {}

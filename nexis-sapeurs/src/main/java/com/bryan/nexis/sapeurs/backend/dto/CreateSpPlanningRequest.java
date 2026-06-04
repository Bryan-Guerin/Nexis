package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record CreateSpPlanningRequest(Instant debut, Instant fin, UUID statutId, String notes) {}

package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CreateSpNatureInterventionRequest(String code, String label) {}

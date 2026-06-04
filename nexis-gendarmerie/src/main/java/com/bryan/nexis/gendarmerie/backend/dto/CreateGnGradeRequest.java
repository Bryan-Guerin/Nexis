package com.bryan.nexis.gendarmerie.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CreateGnGradeRequest(String code, String label) {}

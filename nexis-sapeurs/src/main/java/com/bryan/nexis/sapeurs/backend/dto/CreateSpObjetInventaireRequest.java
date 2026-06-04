package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CreateSpObjetInventaireRequest(String code, String label) {}

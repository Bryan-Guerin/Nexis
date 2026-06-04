package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CreateSpCasierRequest(int numero) {}

package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record CreateSpPosteRequest(UUID fonctionId, short nbPlaces, boolean obligatoire) {}

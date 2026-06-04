package com.bryan.nexis.gendarmerie.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CreateFactionUserRequest(String username, String password) {}

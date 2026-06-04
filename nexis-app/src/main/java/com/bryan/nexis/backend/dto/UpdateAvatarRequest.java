package com.bryan.nexis.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UpdateAvatarRequest(String avatar) {}

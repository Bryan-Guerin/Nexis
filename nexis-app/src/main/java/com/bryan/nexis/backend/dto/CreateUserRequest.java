package com.bryan.nexis.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Set;

@Serdeable
public record CreateUserRequest(
        String username,
        String password,
        Set<String> roles
) {}

package com.bryan.nexis.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

/** Changement de son propre mot de passe. */
@Serdeable
public record ChangePasswordRequest(String currentPassword, String newPassword) {}

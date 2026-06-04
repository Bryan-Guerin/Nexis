package com.bryan.nexis.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

/** Mot de passe temporaire généré, à communiquer à l'utilisateur par l'admin. */
@Serdeable
public record ResetPasswordResponse(String password) {}

package com.bryan.nexis.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/** Création d'un rôle ; {@code parentCode} optionnel (héritage des accès). */
@Serdeable
public record CreateRoleRequest(String code, String label, @Nullable String parentCode) {}

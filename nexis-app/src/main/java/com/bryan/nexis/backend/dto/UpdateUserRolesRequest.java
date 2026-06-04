package com.bryan.nexis.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Set;

/** Remplace l'ensemble des rôles d'un utilisateur. */
@Serdeable
public record UpdateUserRolesRequest(Set<String> roles) {}

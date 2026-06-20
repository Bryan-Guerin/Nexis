package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/** Édition d'une victime (autorisée tant que l'intervention est ouverte). */
@Serdeable
public record UpdateVictimeRequest(@Nullable String libelle, @Nullable String nom,
                                   @Nullable String prenom, @Nullable String sexe) {}

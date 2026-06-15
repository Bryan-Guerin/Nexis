package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

/** Définit la catégorie d'une fonction (CHEF_AGRES / CONDUCTEUR / CHEF_EQUIPE / EQUIPIER). */
@Serdeable
public record SetTypeFonctionRequest(String type) {}

package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/** Création d'une victime (libellé optionnel, ex. « Conducteur »). */
@Serdeable
public record CreateVictimeRequest(@Nullable String libelle) {}

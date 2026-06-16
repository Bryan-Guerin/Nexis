package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

/** Coordonnées jeu (6 chiffres) — null/vide pour effacer. */
@Serdeable
public record SetCoordonneesRequest(String coordonnees) {}

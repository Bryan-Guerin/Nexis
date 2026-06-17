package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

/** Point agrégé de la heatmap : coordonnées 6 chiffres + compteur d'interventions. */
@Serdeable
public record HeatmapPointDto(String coordonnees, int count) {}

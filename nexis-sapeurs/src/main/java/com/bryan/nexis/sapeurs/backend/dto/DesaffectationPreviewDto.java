package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

/** Aperçu d'un effectif qui sera désaffecté au déclenchement (poste non obligatoire). */
@Serdeable
public record DesaffectationPreviewDto(String vehicule, String gradeCode, String nom, String fonction) {}

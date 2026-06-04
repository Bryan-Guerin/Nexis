package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CreateSpVehiculeEtatRequest(String code, String label, String couleur) {}

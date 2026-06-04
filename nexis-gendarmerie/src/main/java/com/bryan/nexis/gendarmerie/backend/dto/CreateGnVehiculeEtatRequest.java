package com.bryan.nexis.gendarmerie.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CreateGnVehiculeEtatRequest(String code, String label, String couleur) {}

package com.bryan.nexis.gendarmerie.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record CreateGnVehiculeRequest(UUID typeId, String libelle, String immatriculation) {}

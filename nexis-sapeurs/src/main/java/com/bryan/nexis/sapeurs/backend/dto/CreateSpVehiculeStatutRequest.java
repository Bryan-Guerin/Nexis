package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record CreateSpVehiculeStatutRequest(String code, String label, String couleur, UUID etatId,
                                            @Nullable Boolean clotureIntervention) {}

package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.core.datamodel.TypeService;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CreateSpPlanningStatutRequest(String code, String label, String couleur, TypeService categorie) {}

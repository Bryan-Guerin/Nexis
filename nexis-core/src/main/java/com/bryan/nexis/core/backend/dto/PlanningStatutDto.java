package com.bryan.nexis.core.backend.dto;

import com.bryan.nexis.core.datamodel.AbstractPlanningStatut;
import com.bryan.nexis.core.datamodel.TypeService;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record PlanningStatutDto(UUID id, String code, String label, String couleur, int position, TypeService categorie) {

    public static PlanningStatutDto from(AbstractPlanningStatut s) {
        return new PlanningStatutDto(s.getId(), s.getCode(), s.getLabel(), s.getCouleur(), s.getPosition(), s.getCategorie());
    }
}

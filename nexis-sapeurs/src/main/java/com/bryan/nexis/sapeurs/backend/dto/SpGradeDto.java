package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpGrade;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.UUID;

@Serdeable
public record SpGradeDto(UUID id, String code, String label, int position,
                         BigDecimal tauxHoraire, BigDecimal tauxAstreinte, boolean peutValiderCri) {

    public static SpGradeDto from(SpGrade g) {
        return new SpGradeDto(g.getId(), g.getCode(), g.getLabel(), g.getPosition(),
                g.getTauxHoraire(), g.getTauxAstreinte(), g.isPeutValiderCri());
    }
}

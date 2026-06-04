package com.bryan.nexis.gendarmerie.backend.dto;

import com.bryan.nexis.gendarmerie.datamodel.GnGrade;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record GnGradeDto(UUID id, String code, String label) {

    public static GnGradeDto from(GnGrade g) {
        return new GnGradeDto(g.getId(), g.getCode(), g.getLabel());
    }
}

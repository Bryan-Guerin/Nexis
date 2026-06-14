package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpPaieVersement;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;

/** Versement de paie vu par le membre concerné (pour la notification « vous avez été payé »). */
@Serdeable
public record SpPaieVersementDto(String semaine, BigDecimal montant, String reglePar, String regleLe) {

    public static SpPaieVersementDto from(SpPaieVersement v) {
        return new SpPaieVersementDto(v.getSemaineLundi().toString(), v.getMontant(),
                v.getReglePar(), v.getRegleLe().toString());
    }
}

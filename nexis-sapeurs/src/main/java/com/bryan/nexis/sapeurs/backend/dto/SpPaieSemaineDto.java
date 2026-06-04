package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Paie hebdomadaire SP : une ligne par membre ayant effectué des heures de garde sur la semaine. */
@Serdeable
public record SpPaieSemaineDto(String debut, String fin, BigDecimal total, List<Ligne> lignes) {

    @Serdeable
    public record Ligne(UUID membreId, String matricule, String username, String grade,
                        double heuresGarde, double heuresAstreinte,
                        BigDecimal tauxHoraire, BigDecimal tauxAstreinte, BigDecimal montant) {}
}

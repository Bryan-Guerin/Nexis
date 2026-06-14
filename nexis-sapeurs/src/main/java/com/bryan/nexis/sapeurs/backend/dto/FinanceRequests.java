package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.UUID;

/** Requêtes du module trésorerie SP. */
public final class FinanceRequests {

    private FinanceRequests() {}

    @Serdeable
    public record UpdateCompteRequest(String libelle, BigDecimal soldeInitial) {}

    @Serdeable
    public record CreateMouvementRequest(String type, BigDecimal montant, String libelle,
                                         String date, UUID categorieId) {}

    @Serdeable
    public record CreateCategorieRequest(String libelle) {}
}

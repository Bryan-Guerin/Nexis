package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpFinanceCategorie;
import com.bryan.nexis.sapeurs.datamodel.SpFinanceMouvement;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Vue trésorerie SP : compte, soldes calculés, catégories et journal des mouvements. */
@Serdeable
public record SpFinanceDto(
        String libelle,
        BigDecimal soldeInitial,
        BigDecimal solde,
        BigDecimal totalGains,
        BigDecimal totalDepenses,
        List<Categorie> categories,
        List<Mouvement> mouvements
) {
    @Serdeable
    public record Categorie(UUID id, String libelle) {
        public static Categorie from(SpFinanceCategorie c) { return new Categorie(c.getId(), c.getLibelle()); }
    }

    @Serdeable
    public record Mouvement(UUID id, String type, BigDecimal montant, String libelle, String date,
                            UUID categorieId, String categorieLibelle, String creePar, String creeLe) {
        public static Mouvement from(SpFinanceMouvement m) {
            return new Mouvement(m.getId(), m.getType(), m.getMontant(), m.getLibelle(),
                    m.getDateMouvement().toString(),
                    m.getCategorie() == null ? null : m.getCategorie().getId(),
                    m.getCategorie() == null ? null : m.getCategorie().getLibelle(),
                    m.getCreePar(), m.getCreeLe().toString());
        }
    }
}

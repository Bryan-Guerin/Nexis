package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Compte de trésorerie SP (unique). Le solde courant = solde initial + gains − dépenses. */
@Entity
@Table(name = "sp_finance_compte")
public class SpFinanceCompte {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String libelle;

    @Column(name = "solde_initial", nullable = false, precision = 12, scale = 2)
    private BigDecimal soldeInitial = BigDecimal.ZERO;

    protected SpFinanceCompte() {}

    public UUID getId()              { return id; }
    public String getLibelle()       { return libelle; }
    public BigDecimal getSoldeInitial() { return soldeInitial; }

    public void setLibelle(String libelle)            { this.libelle = libelle; }
    public void setSoldeInitial(BigDecimal soldeInitial) { this.soldeInitial = soldeInitial; }
}

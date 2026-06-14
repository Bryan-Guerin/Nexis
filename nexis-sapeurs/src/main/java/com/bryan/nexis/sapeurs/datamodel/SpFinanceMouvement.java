package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Mouvement de trésorerie : un gain ou une dépense, historisé (qui, quand). */
@Entity
@Table(name = "sp_finance_mouvement")
public class SpFinanceMouvement {

    public static final String GAIN    = "GAIN";
    public static final String DEPENSE = "DEPENSE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(name = "date_mouvement", nullable = false)
    private LocalDate dateMouvement;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categorie_id")
    private SpFinanceCategorie categorie;

    @Column(name = "cree_par", length = 50) private String creePar;
    @Column(name = "cree_le", nullable = false, updatable = false) private Instant creeLe = Instant.now();

    protected SpFinanceMouvement() {}

    public SpFinanceMouvement(String type, BigDecimal montant, String libelle, LocalDate dateMouvement,
                              SpFinanceCategorie categorie, String creePar) {
        this.type          = type;
        this.montant       = montant;
        this.libelle       = libelle;
        this.dateMouvement = dateMouvement;
        this.categorie     = categorie;
        this.creePar       = creePar;
    }

    public UUID getId()                 { return id; }
    public String getType()             { return type; }
    public BigDecimal getMontant()      { return montant; }
    public String getLibelle()          { return libelle; }
    public LocalDate getDateMouvement() { return dateMouvement; }
    public SpFinanceCategorie getCategorie() { return categorie; }
    public String getCreePar()          { return creePar; }
    public Instant getCreeLe()          { return creeLe; }
}

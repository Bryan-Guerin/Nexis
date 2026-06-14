package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Versement de paie d'un membre pour une semaine donnée (lundi de la semaine).
 * La présence d'au moins une ligne pour une semaine marque celle-ci « réglée ».
 * Irréversible : sert de trace (qui a réglé, quand, combien).
 */
@Entity
@Table(name = "sp_paie_versement",
        uniqueConstraints = @UniqueConstraint(columnNames = {"membre_id", "semaine_lundi"}))
public class SpPaieVersement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private SpMembre membre;

    @Column(name = "semaine_lundi", nullable = false)
    private LocalDate semaineLundi;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montant;

    @Column(name = "regle_par", length = 50) private String reglePar;
    @Column(name = "regle_le", nullable = false, updatable = false) private Instant regleLe = Instant.now();

    protected SpPaieVersement() {}

    public SpPaieVersement(SpMembre membre, LocalDate semaineLundi, BigDecimal montant, String reglePar) {
        this.membre       = membre;
        this.semaineLundi = semaineLundi;
        this.montant      = montant;
        this.reglePar     = reglePar;
    }

    public UUID getId()             { return id; }
    public SpMembre getMembre()     { return membre; }
    public LocalDate getSemaineLundi() { return semaineLundi; }
    public BigDecimal getMontant()  { return montant; }
    public String getReglePar()     { return reglePar; }
    public Instant getRegleLe()     { return regleLe; }
}

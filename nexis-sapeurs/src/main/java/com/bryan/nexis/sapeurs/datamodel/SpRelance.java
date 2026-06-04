package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Rappel manuel (relance) sur un effectif, ex. recyclage/compétence à prévoir. */
@Entity
@Table(name = "sp_relance")
public class SpRelance {

    public static final String OUVERT = "OUVERT";
    public static final String FAIT   = "FAIT";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private SpMembre membre;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String texte;

    @Column
    private LocalDate echeance;

    @Column(nullable = false, length = 10)
    private String statut = OUVERT;

    @Column(name = "cree_par", length = 50) private String creePar;
    @Column(name = "cree_le", nullable = false, updatable = false) private Instant creeLe = Instant.now();
    @Column(name = "fait_par", length = 50) private String faitPar;
    @Column(name = "fait_le")               private Instant faitLe;

    protected SpRelance() {}

    public SpRelance(SpMembre membre, String texte, LocalDate echeance, String creePar) {
        this.membre   = membre;
        this.texte    = texte;
        this.echeance = echeance;
        this.creePar  = creePar;
    }

    public UUID getId()          { return id; }
    public SpMembre getMembre()  { return membre; }
    public String getTexte()     { return texte; }
    public LocalDate getEcheance() { return echeance; }
    public String getStatut()    { return statut; }
    public String getCreePar()   { return creePar; }
    public Instant getCreeLe()   { return creeLe; }
    public String getFaitPar()   { return faitPar; }
    public Instant getFaitLe()   { return faitLe; }

    public void setStatut(String statut)  { this.statut = statut; }
    public void setFaitPar(String faitPar) { this.faitPar = faitPar; }
    public void setFaitLe(Instant faitLe) { this.faitLe = faitLe; }
}

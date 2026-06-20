package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Victime d'une intervention : unité du bilan SAP (un bilan par victime) et ancre du futur
 * dossier médical. 1..n par intervention, numérotées séquentiellement.
 */
@Entity
@Table(name = "sp_victime", uniqueConstraints = @UniqueConstraint(columnNames = {"intervention_id", "numero"}))
public class SpVictime {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intervention_id", nullable = false)
    private SpIntervention intervention;

    @Column(nullable = false)
    private int numero;

    /** Rôle / position (ex. « Conducteur »). Optionnel. */
    @Column(length = 120)
    private String libelle;

    @Column(length = 80)
    private String nom;

    @Column(length = 80)
    private String prenom;

    @Enumerated(EnumType.STRING)
    @Column(length = 1)
    private Sexe sexe;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe = Instant.now();

    protected SpVictime() {}

    public SpVictime(SpIntervention intervention, int numero) {
        this.intervention = intervention;
        this.numero = numero;
    }

    public UUID getId()                     { return id; }
    public SpIntervention getIntervention() { return intervention; }
    public int getNumero()                  { return numero; }
    public String getLibelle()              { return libelle; }
    public String getNom()                  { return nom; }
    public String getPrenom()               { return prenom; }
    public Sexe getSexe()                   { return sexe; }
    public Instant getCreeLe()              { return creeLe; }

    public void setLibelle(String libelle)  { this.libelle = libelle; }
    public void setNom(String nom)          { this.nom = nom; }
    public void setPrenom(String prenom)    { this.prenom = prenom; }
    public void setSexe(Sexe sexe)          { this.sexe = sexe; }
}

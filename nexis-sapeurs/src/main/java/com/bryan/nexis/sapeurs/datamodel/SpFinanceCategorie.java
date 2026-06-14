package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/** Catégorie de mouvement de trésorerie (ex. Carburant, Matériel, Subvention), configurable. */
@Entity
@Table(name = "sp_finance_categorie")
public class SpFinanceCategorie {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String libelle;

    protected SpFinanceCategorie() {}

    public SpFinanceCategorie(String libelle) { this.libelle = libelle; }

    public UUID getId()        { return id; }
    public String getLibelle() { return libelle; }
}

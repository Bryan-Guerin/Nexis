package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/** Dossier / catégorie de documents (ex. Formations, Notes de service). */
@Entity
@Table(name = "sp_document_categorie")
public class SpDocumentCategorie {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String nom;

    @Column(name = "ordre", nullable = false)
    private int position;

    protected SpDocumentCategorie() {}

    public SpDocumentCategorie(String nom) { this.nom = nom; }

    public UUID getId()      { return id; }
    public String getNom()   { return nom; }
    public int getPosition() { return position; }

    public void setPosition(int position) { this.position = position; }
}

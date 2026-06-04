package com.bryan.nexis.core.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Statut de planning configurable, commun en structure à toutes les factions.
 * Chaque module fournit sa table concrète (sp_planning_statut, gn_planning_statut…)
 * en héritant de cette classe.
 */
@MappedSuperclass
public abstract class AbstractPlanningStatut {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(length = 7)
    private String couleur;

    /** Ordre d'affichage (comme l'index d'une enum). */
    @Column(name = "ordre", nullable = false)
    private int position;

    /** Catégorie transverse déterminant notamment la mise « en service ». */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeService categorie;

    protected AbstractPlanningStatut() {}

    protected AbstractPlanningStatut(String code, String label, String couleur, TypeService categorie) {
        this.code      = code;
        this.label     = label;
        this.couleur   = couleur;
        this.categorie = categorie;
    }

    public UUID getId()             { return id; }
    public String getCode()         { return code; }
    public String getLabel()        { return label; }
    public String getCouleur()      { return couleur; }
    public int getPosition()        { return position; }
    public TypeService getCategorie() { return categorie; }

    public void setLabel(String label)        { this.label = label; }
    public void setCouleur(String couleur)    { this.couleur = couleur; }
    public void setPosition(int position)     { this.position = position; }
    public void setCategorie(TypeService c)   { this.categorie = c; }
}

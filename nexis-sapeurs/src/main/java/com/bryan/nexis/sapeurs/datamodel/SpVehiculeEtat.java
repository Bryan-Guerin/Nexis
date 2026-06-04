package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * État véhicule — référentiel système maître (garant du code).
 * Disponible / Maintenance / Inventaire / Indisponible. Configurable.
 */
@Entity
@Table(name = "sp_vehicule_etat")
public class SpVehiculeEtat {

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

    /** Ordre d'affichage. */
    @Column(name = "ordre", nullable = false)
    private int position;

    protected SpVehiculeEtat() {}

    public SpVehiculeEtat(String code, String label, String couleur) {
        this.code    = code;
        this.label   = label;
        this.couleur = couleur;
    }

    public UUID getId()        { return id; }
    public String getCode()    { return code; }
    public String getLabel()   { return label; }
    public String getCouleur() { return couleur; }
    public int getPosition()   { return position; }

    public void setPosition(int position) { this.position = position; }
}

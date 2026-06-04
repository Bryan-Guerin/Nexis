package com.bryan.nexis.gendarmerie.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "gn_vehicule_etat")
public class GnVehiculeEtat {

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

    protected GnVehiculeEtat() {}

    public GnVehiculeEtat(String code, String label, String couleur) {
        this.code    = code;
        this.label   = label;
        this.couleur = couleur;
    }

    public UUID getId()      { return id; }
    public String getCode()  { return code; }
    public String getLabel() { return label; }
    public String getCouleur() { return couleur; }
}

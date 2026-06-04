package com.bryan.nexis.gendarmerie.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "gn_vehicule")
public class GnVehicule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "type_id", nullable = false)
    private GnVehiculeType type;

    @Column(unique = true, length = 20)
    private String immatriculation;

    @Column(nullable = false, length = 100)
    private String libelle;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "etat_id", nullable = false)
    private GnVehiculeEtat etat;

    @Column(columnDefinition = "TEXT")
    private String notes;

    protected GnVehicule() {}

    public GnVehicule(GnVehiculeType type, String libelle, GnVehiculeEtat etat) {
        this.type  = type;
        this.libelle = libelle;
        this.etat  = etat;
    }

    public UUID getId()                { return id; }
    public GnVehiculeType getType()    { return type; }
    public String getImmatriculation() { return immatriculation; }
    public String getLibelle()         { return libelle; }
    public GnVehiculeEtat getEtat()    { return etat; }
    public String getNotes()           { return notes; }

    public void setType(GnVehiculeType type)     { this.type = type; }
    public void setImmatriculation(String immat) { this.immatriculation = immat; }
    public void setLibelle(String libelle)       { this.libelle = libelle; }
    public void setEtat(GnVehiculeEtat etat)     { this.etat = etat; }
    public void setNotes(String notes)           { this.notes = notes; }
}

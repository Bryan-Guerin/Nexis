package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/** Hôpital / centre hospitalier, configurable. Destination de transport, repère carte. */
@Entity
@Table(name = "sp_hopital")
public class SpHopital {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "ordre", nullable = false)
    private int position;

    /** Coordonnées jeu (6 chiffres) de l'hôpital, pour la carte. Optionnel. */
    @Column(length = 6)
    private String coordonnees;

    protected SpHopital() {}

    public SpHopital(String code, String label) {
        this.code = code; this.label = label;
    }

    public UUID getId()            { return id; }
    public String getCode()        { return code; }
    public String getLabel()       { return label; }
    public int getPosition()       { return position; }
    public String getCoordonnees() { return coordonnees; }

    public void setPosition(int position)          { this.position = position; }
    public void setCoordonnees(String coordonnees) { this.coordonnees = coordonnees; }
}

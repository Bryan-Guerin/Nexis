package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/** Fréquence radio (référentiel simple, géré par les admins, affiché à tous les SP). */
@Entity
@Table(name = "sp_frequence_radio")
public class SpFrequenceRadio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String description;

    /** Fréquence au format libre, ex. "150.1". */
    @Column(nullable = false, length = 20)
    private String frequence;

    @Column(name = "ordre", nullable = false)
    private int position;

    protected SpFrequenceRadio() {}

    public SpFrequenceRadio(String description, String frequence) {
        this.description = description;
        this.frequence = frequence;
    }

    public UUID getId()           { return id; }
    public String getDescription() { return description; }
    public String getFrequence()   { return frequence; }
    public int getPosition()       { return position; }

    public void setDescription(String description) { this.description = description; }
    public void setFrequence(String frequence)     { this.frequence = frequence; }
    public void setPosition(int position)          { this.position = position; }
}

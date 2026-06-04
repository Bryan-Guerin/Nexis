package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/** Objet d'inventaire configurable (catalogue). */
@Entity
@Table(name = "sp_objet_inventaire")
public class SpObjetInventaire {

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

    protected SpObjetInventaire() {}

    public SpObjetInventaire(String code, String label) {
        this.code = code; this.label = label;
    }

    public UUID getId()      { return id; }
    public String getCode()  { return code; }
    public String getLabel() { return label; }
    public int getPosition() { return position; }

    public void setPosition(int position) { this.position = position; }
}

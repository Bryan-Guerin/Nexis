package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Casier (vestiaire) configurable. Géré dans la configuration SP, à la manière
 * d'une énumération ordonnée.
 */
@Entity
@Table(name = "sp_casier")
public class SpCasier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private int numero;

    /** Ordre d'affichage (comme l'index d'une enum). */
    @Column(name = "ordre", nullable = false)
    private int position;

    protected SpCasier() {}

    public SpCasier(int numero) {
        this.numero = numero;
    }

    public UUID getId()      { return id; }
    public int getNumero()   { return numero; }
    public int getPosition() { return position; }

    public void setNumero(int numero)     { this.numero = numero; }
    public void setPosition(int position) { this.position = position; }
}

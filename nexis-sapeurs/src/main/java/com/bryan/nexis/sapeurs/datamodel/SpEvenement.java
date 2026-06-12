package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Événement SP (titre + texte + date), créé par les admins ; les effectifs y déclarent leur présence. */
@Entity
@Table(name = "sp_evenement")
public class SpEvenement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String texte;

    @Column(name = "date_evenement", nullable = false)
    private Instant dateEvenement;

    @Column(name = "cree_par", length = 50)
    private String creePar;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe = Instant.now();

    protected SpEvenement() {}

    public SpEvenement(String titre, String texte, Instant dateEvenement, String creePar) {
        this.titre = titre;
        this.texte = texte;
        this.dateEvenement = dateEvenement;
        this.creePar = creePar;
    }

    public UUID getId()             { return id; }
    public String getTitre()        { return titre; }
    public String getTexte()        { return texte; }
    public Instant getDateEvenement() { return dateEvenement; }
    public String getCreePar()      { return creePar; }
    public Instant getCreeLe()      { return creeLe; }

    public void setTitre(String titre)               { this.titre = titre; }
    public void setTexte(String texte)               { this.texte = texte; }
    public void setDateEvenement(Instant d)          { this.dateEvenement = d; }
}

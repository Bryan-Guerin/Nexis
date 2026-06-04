package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Qualification (habilitation) d'un membre à une fonction, avec date de délivrance et délivreur. */
@Entity
@Table(name = "sp_membre_qualification",
       uniqueConstraints = @UniqueConstraint(columnNames = {"membre_id", "fonction_id"}))
public class SpMembreQualification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private SpMembre membre;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fonction_id", nullable = false)
    private SpFonction fonction;

    @Column(name = "date_delivrance", nullable = false)
    private Instant dateDelivrance = Instant.now();

    @Column(name = "delivre_par", length = 50)
    private String delivrePar;

    protected SpMembreQualification() {}

    public SpMembreQualification(SpMembre membre, SpFonction fonction, String delivrePar) {
        this.membre     = membre;
        this.fonction   = fonction;
        this.delivrePar = delivrePar;
    }

    public UUID getId()              { return id; }
    public SpMembre getMembre()      { return membre; }
    public SpFonction getFonction()  { return fonction; }
    public Instant getDateDelivrance() { return dateDelivrance; }
    public String getDelivrePar()    { return delivrePar; }
}

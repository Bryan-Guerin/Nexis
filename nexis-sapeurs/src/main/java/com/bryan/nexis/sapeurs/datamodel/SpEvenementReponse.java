package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Réponse de présence d'un effectif à un événement (présent = true / absent = false). */
@Entity
@Table(name = "sp_evenement_reponse",
       uniqueConstraints = @UniqueConstraint(columnNames = {"evenement_id", "membre_id"}))
public class SpEvenementReponse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "evenement_id", nullable = false)
    private SpEvenement evenement;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private SpMembre membre;

    @Column(nullable = false)
    private boolean present;

    @Column(name = "repondu_le", nullable = false)
    private Instant reponduLe = Instant.now();

    protected SpEvenementReponse() {}

    public SpEvenementReponse(SpEvenement evenement, SpMembre membre, boolean present) {
        this.evenement = evenement;
        this.membre = membre;
        this.present = present;
    }

    public UUID getId()           { return id; }
    public SpEvenement getEvenement() { return evenement; }
    public SpMembre getMembre()   { return membre; }
    public boolean isPresent()    { return present; }
    public Instant getReponduLe() { return reponduLe; }

    public void setPresent(boolean present)     { this.present = present; }
    public void setReponduLe(Instant reponduLe) { this.reponduLe = reponduLe; }
}

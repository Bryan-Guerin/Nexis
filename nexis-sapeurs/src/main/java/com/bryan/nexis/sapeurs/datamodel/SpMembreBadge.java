package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Lien membre ↔ badge obtenu (avec date d'obtention). */
@Entity
@Table(name = "sp_membre_badge",
        uniqueConstraints = @UniqueConstraint(columnNames = {"membre_id", "badge_id"}))
public class SpMembreBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private SpMembre membre;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "badge_id", nullable = false)
    private SpBadge badge;

    @Column(name = "obtenu_le", nullable = false)
    private Instant obtenuLe = Instant.now();

    /** Faux tant que le porteur n'a pas cliqué pour découvrir le badge sur sa fiche. */
    @Column(nullable = false)
    private boolean decouvert = false;

    protected SpMembreBadge() {}

    public SpMembreBadge(SpMembre membre, SpBadge badge) {
        this.membre = membre;
        this.badge  = badge;
    }

    public UUID getId()         { return id; }
    public SpMembre getMembre() { return membre; }
    public SpBadge getBadge()   { return badge; }
    public Instant getObtenuLe(){ return obtenuLe; }
    public boolean isDecouvert(){ return decouvert; }

    public void setDecouvert(boolean decouvert) { this.decouvert = decouvert; }
}

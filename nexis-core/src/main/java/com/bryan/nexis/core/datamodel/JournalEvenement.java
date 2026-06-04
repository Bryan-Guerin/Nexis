package com.bryan.nexis.core.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Trace persistée d'un événement de domaine : socle de la main courante et de l'audit. */
@Entity
@Table(name = "journal_evenement")
public class JournalEvenement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(length = 10)
    private String faction;

    @Column(name = "acteur_username", length = 50)
    private String acteurUsername;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    /** Référence facultative reliant l'événement à un objet (ex. code intervention). */
    @Column(length = 40)
    private String reference;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe = Instant.now();

    protected JournalEvenement() {}

    public JournalEvenement(String type, String faction, String acteurUsername, String message, String reference) {
        this.type           = type;
        this.faction        = faction;
        this.acteurUsername = acteurUsername;
        this.message        = message;
        this.reference      = reference;
    }

    public UUID getId()              { return id; }
    public String getType()          { return type; }
    public String getFaction()       { return faction; }
    public String getActeurUsername() { return acteurUsername; }
    public String getMessage()       { return message; }
    public String getReference()     { return reference; }
    public Instant getCreeLe()       { return creeLe; }
}

package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Vote d'un membre pour l'intervention de la semaine.
 * {@code semaineDate} = lundi de la semaine OÙ s'est déroulée l'intervention
 * (par construction, la semaine précédant celle du vote).
 * Un membre ne peut voter qu'une fois par semaine.
 */
@Entity
@Table(name = "sp_intervention_vote",
        uniqueConstraints = @UniqueConstraint(columnNames = {"membre_id", "semaine_date"}))
public class SpInterventionVote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intervention_id", nullable = false)
    private SpIntervention intervention;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private SpMembre membre;

    @Column(name = "semaine_date", nullable = false)
    private LocalDate semaineDate;

    @Column(name = "vote_le", nullable = false)
    private Instant voteLe = Instant.now();

    protected SpInterventionVote() {}

    public SpInterventionVote(SpIntervention intervention, SpMembre membre, LocalDate semaineDate) {
        this.intervention = intervention;
        this.membre       = membre;
        this.semaineDate  = semaineDate;
    }

    public UUID getId()                  { return id; }
    public SpIntervention getIntervention() { return intervention; }
    public SpMembre getMembre()          { return membre; }
    public LocalDate getSemaineDate()    { return semaineDate; }
    public Instant getVoteLe()           { return voteLe; }

    public void setIntervention(SpIntervention intervention) { this.intervention = intervention; }
    public void setVoteLe(Instant voteLe) { this.voteLe = voteLe; }
}

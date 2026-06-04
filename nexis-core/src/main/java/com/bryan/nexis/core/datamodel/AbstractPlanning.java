package com.bryan.nexis.core.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Plage de planning d'un membre, commune en structure à toutes les factions.
 * Chaque module fournit sa table concrète (sp_planning, gn_planning…) avec sa
 * propre association vers son membre et son référentiel de statut.
 *
 * <p>Les accesseurs abstraits exposent une vue homogène (membre, statut) qui
 * permet une projection générique vers {@code PlanningDto}.</p>
 */
@MappedSuperclass
public abstract class AbstractPlanning {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private Instant debut;

    @Column(nullable = false)
    private Instant fin;

    @Column(columnDefinition = "TEXT")
    private String notes;

    protected AbstractPlanning() {}

    public UUID getId()       { return id; }
    public Instant getDebut() { return debut; }
    public Instant getFin()   { return fin; }
    public String getNotes()  { return notes; }

    public void setDebut(Instant debut) { this.debut = debut; }
    public void setFin(Instant fin)     { this.fin = fin; }
    public void setNotes(String notes)  { this.notes = notes; }

    // ── Vue homogène (implémentée par chaque module) ─────────────────────────
    public abstract UUID getMembreId();
    public abstract String getMembreMatricule();
    public abstract String getMembreUsername();
    public abstract AbstractPlanningStatut getStatutView();
}

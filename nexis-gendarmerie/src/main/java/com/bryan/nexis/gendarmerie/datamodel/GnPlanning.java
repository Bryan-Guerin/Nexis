package com.bryan.nexis.gendarmerie.datamodel;

import com.bryan.nexis.core.datamodel.AbstractPlanning;
import com.bryan.nexis.core.datamodel.AbstractPlanningStatut;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gn_planning")
public class GnPlanning extends AbstractPlanning {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private GnMembre membre;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "statut_id", nullable = false)
    private GnPlanningStatut statut;

    protected GnPlanning() {}

    public GnPlanning(GnMembre membre, Instant debut, Instant fin, GnPlanningStatut statut) {
        this.membre = membre;
        this.statut = statut;
        setDebut(debut);
        setFin(fin);
    }

    public GnMembre getMembre()        { return membre; }
    public GnPlanningStatut getStatut() { return statut; }
    public void setStatut(GnPlanningStatut statut) { this.statut = statut; }

    @Override public UUID getMembreId()                { return membre.getId(); }
    @Override public String getMembreMatricule()       { return membre.getMatricule(); }
    @Override public String getMembreUsername()        { return membre.getUser().getUsername(); }
    @Override public AbstractPlanningStatut getStatutView() { return statut; }
}

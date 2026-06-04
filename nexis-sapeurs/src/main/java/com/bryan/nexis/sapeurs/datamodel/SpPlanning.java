package com.bryan.nexis.sapeurs.datamodel;

import com.bryan.nexis.core.datamodel.AbstractPlanning;
import com.bryan.nexis.core.datamodel.AbstractPlanningStatut;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sp_planning")
public class SpPlanning extends AbstractPlanning {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private SpMembre membre;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "statut_id", nullable = false)
    private SpPlanningStatut statut;

    /** Départ effectif anticipé : la plage reste payée jusqu'à sa fin mais n'est plus "en service". */
    @Column(name = "quitte_le")
    private Instant quitteLe;

    protected SpPlanning() {}

    public SpPlanning(SpMembre membre, Instant debut, Instant fin, SpPlanningStatut statut) {
        this.membre = membre;
        this.statut = statut;
        setDebut(debut);
        setFin(fin);
    }

    public SpMembre getMembre()        { return membre; }
    public SpPlanningStatut getStatut() { return statut; }
    public Instant getQuitteLe()       { return quitteLe; }
    public void setStatut(SpPlanningStatut statut) { this.statut = statut; }
    public void setQuitteLe(Instant quitteLe)      { this.quitteLe = quitteLe; }

    @Override public UUID getMembreId()                { return membre.getId(); }
    @Override public String getMembreMatricule()       { return membre.getMatricule(); }
    @Override public String getMembreUsername()        { return membre.getUser().getUsername(); }
    @Override public AbstractPlanningStatut getStatutView() { return statut; }
}

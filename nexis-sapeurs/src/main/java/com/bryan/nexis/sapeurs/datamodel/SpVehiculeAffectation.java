package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sp_vehicule_affectation")
public class SpVehiculeAffectation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicule_id", nullable = false)
    private SpVehicule vehicule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private SpMembre membre;

    /** Poste occupé. Devient null si le poste/la fonction est supprimé (historique conservé). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poste_id")
    private SpVehiculeTypePoste poste;

    @Column(nullable = false)
    private Instant debut;

    @Column
    private Instant fin;

    protected SpVehiculeAffectation() {}

    public SpVehiculeAffectation(SpVehicule vehicule, SpMembre membre, SpVehiculeTypePoste poste, Instant debut) {
        this.vehicule = vehicule;
        this.membre = membre;
        this.poste = poste;
        this.debut = debut;
    }

    public UUID getId()                      { return id; }
    public SpVehicule getVehicule()          { return vehicule; }
    public SpMembre getMembre()              { return membre; }
    public SpVehiculeTypePoste getPoste()    { return poste; }
    public Instant getDebut()                { return debut; }
    public Instant getFin()                  { return fin; }

    public void setDebut(Instant debut) { this.debut = debut; }
    public void setFin(Instant fin)     { this.fin = fin; }
}

package com.bryan.nexis.gendarmerie.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gn_vehicule_affectation")
public class GnVehiculeAffectation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicule_id", nullable = false)
    private GnVehicule vehicule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private GnMembre membre;

    @Column(nullable = false)
    private Instant debut;

    @Column
    private Instant fin;

    protected GnVehiculeAffectation() {}

    public GnVehiculeAffectation(GnVehicule vehicule, GnMembre membre, Instant debut) {
        this.vehicule = vehicule;
        this.membre = membre;
        this.debut = debut;
    }

    public UUID getId()             { return id; }
    public GnVehicule getVehicule() { return vehicule; }
    public GnMembre getMembre()     { return membre; }
    public Instant getDebut()       { return debut; }
    public Instant getFin()         { return fin; }

    public void setDebut(Instant debut) { this.debut = debut; }
    public void setFin(Instant fin)     { this.fin = fin; }
}

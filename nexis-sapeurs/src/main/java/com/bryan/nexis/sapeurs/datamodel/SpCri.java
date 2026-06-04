package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Compte rendu d'intervention (CRI), un par véhicule engagé.
 * Cycle : BROUILLON → SOUMIS (équipage) → VALIDE (admin SP). Contenu générique pour l'instant.
 */
@Entity
@Table(name = "sp_cri", uniqueConstraints = @UniqueConstraint(columnNames = {"intervention_id", "vehicule_id"}))
public class SpCri {

    public static final String BROUILLON = "BROUILLON";
    public static final String SOUMIS    = "SOUMIS";
    public static final String VALIDE    = "VALIDE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intervention_id", nullable = false)
    private SpIntervention intervention;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "vehicule_id", nullable = false)
    private SpVehicule vehicule;

    @Column(columnDefinition = "TEXT")
    private String contenu;

    @Column(nullable = false, length = 15)
    private String statut = BROUILLON;

    @Column(name = "soumis_par", length = 50) private String soumisPar;
    @Column(name = "soumis_le")               private Instant soumisLe;
    @Column(name = "valide_par", length = 50) private String validePar;
    @Column(name = "valide_le")               private Instant valideLe;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe = Instant.now();

    protected SpCri() {}

    public SpCri(SpIntervention intervention, SpVehicule vehicule) {
        this.intervention = intervention;
        this.vehicule     = vehicule;
    }

    public UUID getId()                  { return id; }
    public SpIntervention getIntervention() { return intervention; }
    public SpVehicule getVehicule()      { return vehicule; }
    public String getContenu()           { return contenu; }
    public String getStatut()            { return statut; }
    public String getSoumisPar()         { return soumisPar; }
    public Instant getSoumisLe()         { return soumisLe; }
    public String getValidePar()         { return validePar; }
    public Instant getValideLe()         { return valideLe; }
    public Instant getCreeLe()           { return creeLe; }

    public void setContenu(String contenu)   { this.contenu = contenu; }
    public void setStatut(String statut)     { this.statut = statut; }
    public void setSoumisPar(String s)       { this.soumisPar = s; }
    public void setSoumisLe(Instant i)       { this.soumisLe = i; }
    public void setValidePar(String s)       { this.validePar = s; }
    public void setValideLe(Instant i)       { this.valideLe = i; }
}

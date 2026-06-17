package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "sp_intervention")
public class SpIntervention {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** Numéro séquentiel ; le code affiché en dérive (INT-xxxx). */
    @Column
    private Integer numero;

    @Column(nullable = false, length = 200)
    private String motif;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "nature_id", nullable = false)
    private SpNatureIntervention nature;

    @Column(length = 40)
    private String requerant;

    @Column(length = 10)
    private String telephone;

    @Column(columnDefinition = "TEXT")
    private String observation;

    @Column(length = 40)
    private String commune;

    @Column(length = 6)
    private String coordonnees;

    @Column(nullable = false)
    private Instant debut = Instant.now();

    @Column
    private Instant fin;

    @Column(name = "cree_par", length = 50)
    private String creePar;

    // ── Qualification (arbre décision) ─────────────────────────────────────────
    @Column(name = "nb_victimes")
    private Integer nbVictimes;

    @Column(nullable = false)
    private boolean incendie = false;

    @Column(name = "vehicule_implique", nullable = false)
    private boolean vehiculeImplique = false;

    // ── Renforts (statut éditable par tous) ─────────────────────────────────────
    public static final String RENFORT_NON_PREVENU = "NON_PREVENU";
    public static final String RENFORT_PREVENU     = "PREVENU";
    public static final String RENFORT_SUR_PLACE   = "SUR_PLACE";

    @Column(name = "renfort_gn", nullable = false, length = 15)
    private String renfortGn = RENFORT_NON_PREVENU;

    @Column(name = "renfort_vinci", nullable = false, length = 15)
    private String renfortVinci = RENFORT_NON_PREVENU;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "sp_intervention_vehicule",
        joinColumns = @JoinColumn(name = "intervention_id"),
        inverseJoinColumns = @JoinColumn(name = "vehicule_id")
    )
    private Set<SpVehicule> engins = new HashSet<>();

    /** Snapshot des engins + équipages, figé à la clôture (archive sans FK véhicule). */
    @OneToMany(mappedBy = "intervention", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<SpInterventionEngin> enginsHisto = new ArrayList<>();

    protected SpIntervention() {}

    public SpIntervention(String motif, String creePar) {
        this.motif   = motif;
        this.creePar = creePar;
    }

    public UUID getId()                  { return id; }
    public Integer getNumero()           { return numero; }
    public String getMotif()             { return motif; }
    public SpNatureIntervention getNature() { return nature; }
    public String getRequerant()         { return requerant; }
    public String getTelephone()         { return telephone; }
    public String getObservation()       { return observation; }
    public String getCommune()           { return commune; }
    public String getCoordonnees()       { return coordonnees; }
    public Instant getDebut()            { return debut; }
    public Instant getFin()              { return fin; }
    public String getCreePar()           { return creePar; }
    public Integer getNbVictimes()       { return nbVictimes; }
    public boolean isIncendie()          { return incendie; }
    public boolean isVehiculeImplique()  { return vehiculeImplique; }
    public String getRenfortGn()         { return renfortGn; }
    public String getRenfortVinci()      { return renfortVinci; }
    public Set<SpVehicule> getEngins()   { return engins; }
    public List<SpInterventionEngin> getEnginsHisto() { return enginsHisto; }

    /** Code affiché, dérivé du numéro (ex. INT-0007). */
    public String getCode() { return numero != null ? String.format("INT-%04d", numero) : null; }

    public void setNumero(Integer numero)          { this.numero = numero; }
    public void setMotif(String motif)             { this.motif = motif; }
    public void setNature(SpNatureIntervention n)  { this.nature = n; }
    public void setRequerant(String requerant)     { this.requerant = requerant; }
    public void setTelephone(String telephone)     { this.telephone = telephone; }
    public void setObservation(String observation) { this.observation = observation; }
    public void setCommune(String commune)         { this.commune = commune; }
    public void setCoordonnees(String coordonnees) { this.coordonnees = coordonnees; }
    public void setFin(Instant fin)                { this.fin = fin; }
    public void setNbVictimes(Integer nbVictimes)  { this.nbVictimes = nbVictimes; }
    public void setIncendie(boolean incendie)      { this.incendie = incendie; }
    public void setVehiculeImplique(boolean v)     { this.vehiculeImplique = v; }
    public void setRenfortGn(String r)             { this.renfortGn = r; }
    public void setRenfortVinci(String r)          { this.renfortVinci = r; }
}

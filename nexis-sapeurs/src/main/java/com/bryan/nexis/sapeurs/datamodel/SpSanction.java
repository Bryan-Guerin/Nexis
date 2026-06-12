package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Sanction disciplinaire portée sur un effectif SP (gérée par RH / admin). */
@Entity
@Table(name = "sp_sanction")
public class SpSanction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private SpMembre membre;

    /** Type libre (ex. Avertissement, Blâme, Mise à pied…). Optionnel. */
    @Column(length = 40)
    private String type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String motif;

    @Column(name = "date_sanction", nullable = false)
    private LocalDate dateSanction;

    @Column(name = "cree_par", length = 50) private String creePar;
    @Column(name = "cree_le", nullable = false, updatable = false) private Instant creeLe = Instant.now();

    protected SpSanction() {}

    public SpSanction(SpMembre membre, String type, String motif, LocalDate dateSanction, String creePar) {
        this.membre       = membre;
        this.type         = type;
        this.motif        = motif;
        this.dateSanction = dateSanction;
        this.creePar      = creePar;
    }

    public UUID getId()              { return id; }
    public SpMembre getMembre()      { return membre; }
    public String getType()          { return type; }
    public String getMotif()         { return motif; }
    public LocalDate getDateSanction() { return dateSanction; }
    public String getCreePar()       { return creePar; }
    public Instant getCreeLe()       { return creeLe; }
}

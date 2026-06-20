package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Bilan d'intervention, par famille (SAP / SR / INC). Distinct du CRI (compte rendu par véhicule).
 *
 * <p>Le contenu est un <b>document JSON</b> (colonne {@code jsonb}) dont la forme est typée côté
 * Java par des records propres à chaque famille → extensible sans migration (ajouter un champ ne
 * casse pas les bilans existants). SAP : un bilan par {@link SpVictime}. SR / INC : un bilan par
 * intervention (victime {@code null}).</p>
 */
@Entity
@Table(name = "sp_bilan")
public class SpBilan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intervention_id", nullable = false)
    private SpIntervention intervention;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FamilleBilan famille;

    /** Victime concernée (SAP uniquement ; null pour SR / INC). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "victime_id")
    private SpVictime victime;

    /** Contenu sérialisé en JSON (jsonb). Forme typée par les records de la famille. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String contenu = "{}";

    @Column(length = 50)
    private String auteur;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe = Instant.now();

    @Column(name = "maj_le")
    private Instant majLe;

    protected SpBilan() {}

    public SpBilan(SpIntervention intervention, FamilleBilan famille, SpVictime victime) {
        this.intervention = intervention;
        this.famille = famille;
        this.victime = victime;
    }

    public UUID getId()                     { return id; }
    public SpIntervention getIntervention() { return intervention; }
    public FamilleBilan getFamille()        { return famille; }
    public SpVictime getVictime()           { return victime; }
    public String getContenu()              { return contenu; }
    public String getAuteur()               { return auteur; }
    public Instant getCreeLe()              { return creeLe; }
    public Instant getMajLe()               { return majLe; }

    public void setContenu(String contenu)  { this.contenu = contenu; }
    public void setAuteur(String auteur)    { this.auteur = auteur; }
    public void setMajLe(Instant majLe)     { this.majLe = majLe; }
}

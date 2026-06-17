package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot d'un engin engagé sur une intervention, figé à la clôture (texte, sans FK
 * véhicule). Conserve l'archive même si le véhicule est supprimé ensuite.
 */
@Entity
@Table(name = "sp_intervention_engin")
public class SpInterventionEngin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intervention_id", nullable = false)
    private SpIntervention intervention;

    @Column(nullable = false, length = 100)
    private String libelle;

    @Column(name = "type_code", length = 30)
    private String typeCode;

    @Column(name = "ordre", nullable = false)
    private int position;

    @OneToMany(mappedBy = "engin", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<SpInterventionEquipier> equipage = new ArrayList<>();

    protected SpInterventionEngin() {}

    public SpInterventionEngin(SpIntervention intervention, String libelle, String typeCode, int position) {
        this.intervention = intervention;
        this.libelle      = libelle;
        this.typeCode     = typeCode;
        this.position     = position;
    }

    public UUID getId()                  { return id; }
    public SpIntervention getIntervention() { return intervention; }
    public String getLibelle()           { return libelle; }
    public String getTypeCode()          { return typeCode; }
    public int getPosition()             { return position; }
    public List<SpInterventionEquipier> getEquipage() { return equipage; }
}

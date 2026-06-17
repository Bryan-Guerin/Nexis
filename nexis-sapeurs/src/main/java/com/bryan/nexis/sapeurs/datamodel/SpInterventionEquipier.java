package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/** Snapshot d'un équipier (texte) figé à la clôture, rattaché à un engin historisé. */
@Entity
@Table(name = "sp_intervention_equipier")
public class SpInterventionEquipier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engin_id", nullable = false)
    private SpInterventionEngin engin;

    @Column(length = 20)
    private String matricule;

    @Column(length = 100)
    private String nom;

    @Column(length = 100)
    private String grade;

    @Column(length = 100)
    private String poste;

    @Column(name = "ordre", nullable = false)
    private int position;

    protected SpInterventionEquipier() {}

    public SpInterventionEquipier(SpInterventionEngin engin, String matricule, String nom,
                                  String grade, String poste, int position) {
        this.engin     = engin;
        this.matricule = matricule;
        this.nom       = nom;
        this.grade     = grade;
        this.poste     = poste;
        this.position  = position;
    }

    public UUID getId()               { return id; }
    public SpInterventionEngin getEngin() { return engin; }
    public String getMatricule()      { return matricule; }
    public String getNom()            { return nom; }
    public String getGrade()          { return grade; }
    public String getPoste()          { return poste; }
    public int getPosition()          { return position; }
}

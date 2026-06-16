package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Statut véhicule — référentiel RP ordonné et configurable.
 * Chaque statut est lié à un {@link SpVehiculeEtat} : le basculer applique cet état
 * au véhicule. L'ordre porte la règle métier « transition avant uniquement ».
 */
@Entity
@Table(name = "sp_vehicule_statut")
public class SpVehiculeStatut {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(length = 7)
    private String couleur;

    /** Index dans la progression (0 = premier, appliqué au déclenchement). */
    @Column(name = "ordre", nullable = false)
    private int position;

    /** Conservé pour compatibilité (le déclenchement utilise désormais le premier de la liste). */
    @Column(name = "par_defaut", nullable = false)
    private boolean parDefaut;

    /** État maître appliqué au véhicule quand ce statut est sélectionné. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "etat_id", nullable = false)
    private SpVehiculeEtat etat;

    /**
     * Ce statut valide-t-il la clôture automatique d'intervention ?
     * L'intervention se clôture quand TOUS ses engins portent un statut coché.
     * Permet « Disponible radio » : véhicule libéré sans fermer l'intervention.
     */
    @Column(name = "cloture_intervention", nullable = false)
    private boolean clotureIntervention;

    /** Action carte branchée sur ce statut (transport hôpital, sur place, retour caserne…). */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_carte", nullable = false, length = 20)
    private StatutActionCarte actionCarte = StatutActionCarte.AUCUNE;

    protected SpVehiculeStatut() {}

    public SpVehiculeStatut(String code, String label, String couleur, SpVehiculeEtat etat) {
        this.code    = code;
        this.label   = label;
        this.couleur = couleur;
        this.etat    = etat;
    }

    public UUID getId()          { return id; }
    public String getCode()      { return code; }
    public String getLabel()     { return label; }
    public String getCouleur()   { return couleur; }
    public int getPosition()     { return position; }
    public boolean isParDefaut() { return parDefaut; }
    public SpVehiculeEtat getEtat() { return etat; }
    public boolean isClotureIntervention() { return clotureIntervention; }
    public StatutActionCarte getActionCarte() { return actionCarte; }

    public void setPosition(int position)       { this.position = position; }
    public void setParDefaut(boolean parDefaut) { this.parDefaut = parDefaut; }
    public void setEtat(SpVehiculeEtat etat)    { this.etat = etat; }
    public void setClotureIntervention(boolean v) { this.clotureIntervention = v; }
    public void setActionCarte(StatutActionCarte a) { this.actionCarte = a == null ? StatutActionCarte.AUCUNE : a; }
}

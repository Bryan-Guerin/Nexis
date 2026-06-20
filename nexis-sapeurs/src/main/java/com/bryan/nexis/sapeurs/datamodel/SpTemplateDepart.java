package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Ligne d'un « lot de départ » : pour une nature d'intervention, un type de véhicule et une
 * quantité à engager. À la création, « Engager le lot » sélectionne les N véhicules disponibles
 * de ce type (ranking habituel).
 */
@Entity
@Table(name = "sp_template_depart",
        uniqueConstraints = @UniqueConstraint(columnNames = {"nature_id", "vehicule_type_id"}))
public class SpTemplateDepart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** Déclencheur « nature » (exclusif avec {@link #declencheurFlag}). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "nature_id")
    private SpNatureIntervention nature;

    /** Déclencheur « flag » (incendie, SR, véhicule impliqué) — exclusif avec {@link #nature}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "declencheur_flag", length = 30)
    private DeclencheurFlag declencheurFlag;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "vehicule_type_id", nullable = false)
    private SpVehiculeType vehiculeType;

    @Column(nullable = false)
    private int quantite = 1;

    /** Note libre sur la ligne du lot (ex. « 1er VSAV prioritaire »). Optionnel. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Image-icône optionnelle illustrant la ligne (sinon celle du type de véhicule). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icone_image_id")
    private SpIcone iconeImage;

    @Column(name = "ordre", nullable = false)
    private int position;

    protected SpTemplateDepart() {}

    public SpTemplateDepart(SpNatureIntervention nature, SpVehiculeType vehiculeType, int quantite) {
        this.nature       = nature;
        this.vehiculeType = vehiculeType;
        this.quantite     = quantite;
    }

    public SpTemplateDepart(DeclencheurFlag declencheurFlag, SpVehiculeType vehiculeType, int quantite) {
        this.declencheurFlag = declencheurFlag;
        this.vehiculeType    = vehiculeType;
        this.quantite        = quantite;
    }

    public UUID getId()                     { return id; }
    public SpNatureIntervention getNature() { return nature; }
    public DeclencheurFlag getDeclencheurFlag() { return declencheurFlag; }
    public SpVehiculeType getVehiculeType() { return vehiculeType; }
    public int getQuantite()                { return quantite; }
    public String getDescription()          { return description; }
    public SpIcone getIconeImage()          { return iconeImage; }
    public int getPosition()                { return position; }

    public void setQuantite(int quantite)  { this.quantite = quantite; }
    public void setDescription(String description) { this.description = description; }
    public void setIconeImage(SpIcone iconeImage)  { this.iconeImage = iconeImage; }
    public void setPosition(int position)  { this.position = position; }
}

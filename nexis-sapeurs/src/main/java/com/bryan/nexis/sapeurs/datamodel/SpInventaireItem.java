package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/** Ligne du modèle d'inventaire d'un type de véhicule : un objet + la quantité attendue. */
@Entity
@Table(name = "sp_inventaire_item")
public class SpInventaireItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "vehicule_type_id", nullable = false)
    private SpVehiculeType vehiculeType;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "objet_id", nullable = false)
    private SpObjetInventaire objet;

    @Column(name = "quantite", nullable = false)
    private int quantite = 1;

    @Column(name = "ordre", nullable = false)
    private int position;

    /** Item contenant (sac/lot) ; null = item de premier niveau. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    private SpInventaireItem parent;

    protected SpInventaireItem() {}

    public SpInventaireItem(SpVehiculeType vehiculeType, SpObjetInventaire objet, int quantite) {
        this.vehiculeType = vehiculeType;
        this.objet = objet;
        this.quantite = quantite;
    }

    public UUID getId()                     { return id; }
    public SpVehiculeType getVehiculeType() { return vehiculeType; }
    public SpObjetInventaire getObjet()     { return objet; }
    public int getQuantite()                { return quantite; }
    public int getPosition()                { return position; }
    public SpInventaireItem getParent()     { return parent; }

    public void setQuantite(int quantite)  { this.quantite = quantite; }
    public void setPosition(int position)  { this.position = position; }
    public void setParent(SpInventaireItem parent) { this.parent = parent; }
}

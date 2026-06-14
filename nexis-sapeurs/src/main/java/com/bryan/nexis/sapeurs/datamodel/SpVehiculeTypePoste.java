package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "sp_vehicule_type_poste",
    uniqueConstraints = @UniqueConstraint(columnNames = {"vehicule_type_id", "fonction_id"})
)
public class SpVehiculeTypePoste {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "vehicule_type_id", nullable = false)
    private SpVehiculeType vehiculeType;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fonction_id", nullable = false)
    private SpFonction fonction;

    @Column(name = "nb_places", nullable = false)
    private short nbPlaces = 1;

    /** Poste requis pour qu'un véhicule de ce type soit « armé ». */
    @Column(nullable = false)
    private boolean obligatoire = false;

    /** Ordre d'affichage du poste au sein du type (et de l'équipage au dispatch). */
    @Column(nullable = false)
    private int ordre = 0;

    protected SpVehiculeTypePoste() {}

    public SpVehiculeTypePoste(SpVehiculeType vehiculeType, SpFonction fonction, short nbPlaces, boolean obligatoire) {
        this.vehiculeType = vehiculeType;
        this.fonction     = fonction;
        this.nbPlaces     = nbPlaces;
        this.obligatoire  = obligatoire;
    }

    public UUID getId()                     { return id; }
    public SpVehiculeType getVehiculeType() { return vehiculeType; }
    public SpFonction getFonction()         { return fonction; }
    public short getNbPlaces()              { return nbPlaces; }
    public boolean isObligatoire()          { return obligatoire; }
    public int getOrdre()                   { return ordre; }

    public void setFonction(SpFonction fonction)   { this.fonction = fonction; }
    public void setNbPlaces(short nbPlaces)        { this.nbPlaces = nbPlaces; }
    public void setObligatoire(boolean obligatoire) { this.obligatoire = obligatoire; }
    public void setOrdre(int ordre)                 { this.ordre = ordre; }
}

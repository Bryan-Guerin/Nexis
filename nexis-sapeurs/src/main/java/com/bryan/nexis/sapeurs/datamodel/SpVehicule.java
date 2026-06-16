package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "sp_vehicule")
public class SpVehicule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "type_id", nullable = false)
    private SpVehiculeType type;

    @Column(unique = true, length = 20)
    private String immatriculation;

    @Column(nullable = false, length = 100)
    private String libelle;

    /** État maître (système, garant du code) : Disponible / Maintenance / Inventaire / Indisponible. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "etat_id", nullable = false)
    private SpVehiculeEtat etat;

    /** Statut RP (ordonné, configurable) — la bascule applique l'état lié. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "statut_id", nullable = false)
    private SpVehiculeStatut statut;

    /** Centre / caserne de rattachement — optionnel. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "centre_id")
    private SpCentre centre;

    /** Hôpital de destination courant (transport) — posé au passage en statut transport, effacé au retour. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hopital_destination_id")
    private SpHopital hopitalDestination;

    /** Dernière position connue (coords 6 ch.) : point où l'engin s'est mis « sur place ».
     *  Sert d'origine au trajet de retour (ex. retour depuis l'hôpital, pas depuis l'intervention). */
    @Column(name = "position_coordonnees", length = 6)
    private String positionCoordonnees;

    /** Instant de début du trajet courant (dernier changement de statut) : anime le 🚒 et
     *  calcule l'ETA sur l'aller / le transport / le retour. */
    @Column(name = "leg_depart")
    private java.time.Instant legDepart;

    /** Capacité en eau (litres) — optionnel. */
    @Column(name = "capacite_eau")
    private Integer capaciteEau;

    @Column(columnDefinition = "TEXT")
    private String notes;

    protected SpVehicule() {}

    public SpVehicule(SpVehiculeType type, String libelle, SpVehiculeEtat etat, SpVehiculeStatut statut) {
        this.type    = type;
        this.libelle = libelle;
        this.etat    = etat;
        this.statut  = statut;
    }

    public UUID getId()                     { return id; }
    public SpVehiculeType getType()         { return type; }
    public String getImmatriculation()      { return immatriculation; }
    public String getLibelle()              { return libelle; }
    public SpVehiculeEtat getEtat()         { return etat; }
    public SpVehiculeStatut getStatut()     { return statut; }
    public SpCentre getCentre()             { return centre; }
    public SpHopital getHopitalDestination() { return hopitalDestination; }
    public String getPositionCoordonnees()  { return positionCoordonnees; }
    public java.time.Instant getLegDepart() { return legDepart; }
    public Integer getCapaciteEau()         { return capaciteEau; }
    public String getNotes()                { return notes; }

    public void setType(SpVehiculeType type)              { this.type = type; }
    public void setImmatriculation(String immat)          { this.immatriculation = immat; }
    public void setLibelle(String libelle)                { this.libelle = libelle; }
    public void setEtat(SpVehiculeEtat etat)              { this.etat = etat; }
    public void setStatut(SpVehiculeStatut statut)        { this.statut = statut; }
    public void setCentre(SpCentre centre)                { this.centre = centre; }
    public void setHopitalDestination(SpHopital h)        { this.hopitalDestination = h; }
    public void setPositionCoordonnees(String c)          { this.positionCoordonnees = c; }
    public void setLegDepart(java.time.Instant t)         { this.legDepart = t; }
    public void setCapaciteEau(Integer capaciteEau)       { this.capaciteEau = capaciteEau; }
    public void setNotes(String notes)                    { this.notes = notes; }

    // ── Représentations ───────────────────────────────────────────────────────

    /** Libellé lisible pour l'historique / la main courante / le BIP (sans identifiants). */
    @Override
    public String toString() {
        return immatriculation != null ? libelle + " (" + immatriculation + ")" : libelle;
    }

    /** Représentation technique pour les logs applicatifs (avec identifiants). */
    public String toStringLog() {
        return "SpVehicule[id=" + id + ", libelle=" + libelle + ", immat=" + immatriculation
                + ", type=" + type.getCode() + ", etat=" + etat.getCode() + ", statut=" + statut.getCode() + "]";
    }
}

package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Session de vérification d'inventaire d'un véhicule (avec historique). */
@Entity
@Table(name = "sp_verification")
public class SpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicule_id", nullable = false)
    private SpVehicule vehicule;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe = Instant.now();

    @Column(length = 50)
    private String par;

    /** Conformité globale : vraie si toutes les lignes sont conformes. */
    @Column(nullable = false)
    private boolean conforme = true;

    @OneToMany(mappedBy = "verification", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SpVerificationLigne> lignes = new ArrayList<>();

    protected SpVerification() {}

    public SpVerification(SpVehicule vehicule, String par) {
        this.vehicule = vehicule;
        this.par = par;
    }

    public UUID getId()                      { return id; }
    public SpVehicule getVehicule()          { return vehicule; }
    public Instant getCreeLe()               { return creeLe; }
    public String getPar()                   { return par; }
    public boolean isConforme()              { return conforme; }
    public List<SpVerificationLigne> getLignes() { return lignes; }

    public void setConforme(boolean conforme) { this.conforme = conforme; }

    public void addLigne(SpVerificationLigne ligne) {
        ligne.setVerification(this);
        lignes.add(ligne);
    }
}

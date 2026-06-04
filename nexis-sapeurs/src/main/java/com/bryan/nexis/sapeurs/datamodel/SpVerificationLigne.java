package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/** Ligne d'une vérification : quantité attendue vs constatée pour un objet (libellé figé). */
@Entity
@Table(name = "sp_verification_ligne")
public class SpVerificationLigne {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verification_id", nullable = false)
    private SpVerification verification;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(name = "quantite_attendue", nullable = false)
    private int quantiteAttendue = 1;

    @Column(name = "quantite_presente", nullable = false)
    private int quantitePresente = 0;

    @Column(nullable = false)
    private boolean conforme = true;

    protected SpVerificationLigne() {}

    public SpVerificationLigne(String libelle, int quantiteAttendue, int quantitePresente) {
        this.libelle = libelle;
        this.quantiteAttendue = quantiteAttendue;
        this.quantitePresente = quantitePresente;
        this.conforme = quantitePresente >= quantiteAttendue;
    }

    public UUID getId()            { return id; }
    public String getLibelle()     { return libelle; }
    public int getQuantiteAttendue() { return quantiteAttendue; }
    public int getQuantitePresente() { return quantitePresente; }
    public boolean isConforme()    { return conforme; }

    public void setVerification(SpVerification verification) { this.verification = verification; }
}

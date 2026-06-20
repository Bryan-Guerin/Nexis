package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Image-icône réutilisable (badge, nature, fonction orga, type de véhicule…), stockée en base (bytea). */
@Entity
@Table(name = "sp_icone")
public class SpIcone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long taille;

    @Column(name = "contenu", nullable = false, columnDefinition = "bytea")
    private byte[] contenu;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe = Instant.now();

    @Column(name = "cree_par", length = 50)
    private String creePar;

    protected SpIcone() {}

    public SpIcone(String nom, String contentType, long taille, byte[] contenu, String creePar) {
        this.nom         = nom;
        this.contentType = contentType;
        this.taille      = taille;
        this.contenu     = contenu;
        this.creePar     = creePar;
    }

    public UUID getId()            { return id; }
    public String getNom()         { return nom; }
    public String getContentType() { return contentType; }
    public long getTaille()        { return taille; }
    public byte[] getContenu()     { return contenu; }
    public Instant getCreeLe()     { return creeLe; }
    public String getCreePar()     { return creePar; }
}

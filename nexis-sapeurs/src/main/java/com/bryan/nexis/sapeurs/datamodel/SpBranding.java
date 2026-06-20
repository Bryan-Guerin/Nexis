package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/** Branding de l'instance (ligne unique) : logo de la caserne choisi dans la bibliothèque d'icônes. */
@Entity
@Table(name = "sp_branding")
public class SpBranding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_icone_id")
    private SpIcone logoIcone;

    public UUID getId()             { return id; }
    public SpIcone getLogoIcone()   { return logoIcone; }
    public void setLogoIcone(SpIcone logoIcone) { this.logoIcone = logoIcone; }
}

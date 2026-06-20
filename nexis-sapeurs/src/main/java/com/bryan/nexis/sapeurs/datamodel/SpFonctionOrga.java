package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Fonction dans l'organigramme de la caserne (Chef de centre, RH, Chef de garde, Formateur…).
 * Structurée en arbre via {@code parent} (auto-référencé). Un membre peut cumuler plusieurs
 * fonctions (table d'association {@code sp_membre_fonction_orga}).
 */
@Entity
@Table(name = "sp_fonction_orga")
public class SpFonctionOrga {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    /** Fonction parente dans l'arbre. Null = racine (ex. Chef de centre). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private SpFonctionOrga parent;

    /** Ordre d'affichage parmi les frères (même parent). */
    @Column(name = "ordre", nullable = false)
    private int position;

    /** Emoji optionnel pour repérer la fonction visuellement. */
    @Column(length = 8)
    private String icone;

    /** Image-icône optionnelle (remplace l'emoji si définie). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icone_image_id")
    private SpIcone iconeImage;

    protected SpFonctionOrga() {}

    public SpFonctionOrga(String code, String label) {
        this.code  = code;
        this.label = label;
    }

    public UUID getId()              { return id; }
    public String getCode()          { return code; }
    public String getLabel()         { return label; }
    public SpFonctionOrga getParent() { return parent; }
    public int getPosition()         { return position; }
    public String getIcone()         { return icone; }
    public SpIcone getIconeImage()   { return iconeImage; }

    public void setLabel(String label)   { this.label = label; }
    public void setParent(SpFonctionOrga parent) { this.parent = parent; }
    public void setPosition(int position) { this.position = position; }
    public void setIcone(String icone)   { this.icone = icone; }
    public void setIconeImage(SpIcone iconeImage) { this.iconeImage = iconeImage; }
}

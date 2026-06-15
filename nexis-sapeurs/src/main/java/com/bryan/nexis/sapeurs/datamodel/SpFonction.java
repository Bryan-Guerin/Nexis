package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "sp_fonction")
public class SpFonction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    /** Ordre d'affichage de la liste des fonctions (basique → complexe). */
    @Column(name = "ordre", nullable = false)
    private int position;

    /** Catégorie → ordre de l'équipage au dispatch (CA, Conducteur, Chef d'équipe, Équipier). */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_fonction", nullable = false, length = 20)
    private TypeFonction typeFonction = TypeFonction.EQUIPIER;

    protected SpFonction() {}

    public SpFonction(String code, String label) {
        this.code  = code;
        this.label = label;
    }

    public UUID getId()      { return id; }
    public String getCode()  { return code; }
    public String getLabel() { return label; }
    public int getPosition() { return position; }
    public TypeFonction getTypeFonction() { return typeFonction; }

    public void setPosition(int position) { this.position = position; }
    public void setTypeFonction(TypeFonction typeFonction) { this.typeFonction = typeFonction; }
}

package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Badge (succès) du catalogue. L'attribution est automatique selon
 * {@link BadgeCondition} + {@code seuil}, vérifiée par le service d'évaluation
 * sur les compteurs du membre.
 *
 * <p>Visible par tous les membres SP (élément de reconnaissance public).
 * Attribution non révocable une fois obtenu.</p>
 */
@Entity
@Table(name = "sp_badge")
public class SpBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(length = 8)
    private String icone;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_condition", nullable = false, length = 30)
    private BadgeCondition typeCondition;

    /** Nature ciblée si {@link BadgeCondition#INTER_NATURE_COUNT}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nature_id")
    private SpNatureIntervention nature;

    @Column(nullable = false)
    private int seuil;

    /** XP attribué à l'obtention du badge. */
    @Column(name = "xp_reward", nullable = false)
    private int xpReward = 0;

    /** Ordre d'affichage dans le catalogue. */
    @Column(name = "ordre", nullable = false)
    private int position;

    protected SpBadge() {}

    public SpBadge(String code, String label, BadgeCondition typeCondition, int seuil) {
        this.code          = code;
        this.label         = label;
        this.typeCondition = typeCondition;
        this.seuil         = seuil;
    }

    public UUID getId()                       { return id; }
    public String getCode()                   { return code; }
    public String getLabel()                  { return label; }
    public String getIcone()                  { return icone; }
    public String getDescription()            { return description; }
    public BadgeCondition getTypeCondition()  { return typeCondition; }
    public SpNatureIntervention getNature()   { return nature; }
    public int getSeuil()                     { return seuil; }
    public int getXpReward()                  { return xpReward; }
    public int getPosition()                  { return position; }

    public void setLabel(String label)               { this.label = label; }
    public void setIcone(String icone)               { this.icone = icone; }
    public void setDescription(String description)   { this.description = description; }
    public void setTypeCondition(BadgeCondition t)   { this.typeCondition = t; }
    public void setNature(SpNatureIntervention n)    { this.nature = n; }
    public void setSeuil(int seuil)                  { this.seuil = seuil; }
    public void setXpReward(int xpReward)            { this.xpReward = xpReward; }
    public void setPosition(int position)            { this.position = position; }
}

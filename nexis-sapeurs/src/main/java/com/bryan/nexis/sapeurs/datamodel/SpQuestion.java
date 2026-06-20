package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Question du questionnaire guidé du dispatcher. Configurable (admin), ordonnée, à conditions.
 * La réponse peut préremplir un champ d'intervention ({@link CibleQuestion}) et/ou suggérer une
 * nature (avec son lot de départ). {@code conditionQuestion} : n'apparaît que si la question
 * parente (OUI_NON) a la réponse {@code conditionAttendue}.
 */
@Entity
@Table(name = "sp_question")
public class SpQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeQuestion type;

    @Column(name = "ordre", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CibleQuestion cible = CibleQuestion.AUCUNE;

    /** Nature proposée si la réponse est positive (oui, ou nombre > 0). Optionnel. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nature_suggeree_id")
    private SpNatureIntervention natureSuggeree;

    /** Question parente (OUI_NON) conditionnant l'affichage. Null = toujours affichée. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condition_question_id")
    private SpQuestion conditionQuestion;

    /** Réponse attendue à la question parente pour afficher celle-ci (true = oui). */
    @Column(name = "condition_attendue", nullable = false)
    private boolean conditionAttendue = true;

    protected SpQuestion() {}

    public SpQuestion(String libelle, TypeQuestion type) {
        this.libelle = libelle;
        this.type    = type;
    }

    public UUID getId()                       { return id; }
    public String getLibelle()                { return libelle; }
    public TypeQuestion getType()             { return type; }
    public int getPosition()                  { return position; }
    public CibleQuestion getCible()           { return cible; }
    public SpNatureIntervention getNatureSuggeree() { return natureSuggeree; }
    public SpQuestion getConditionQuestion()  { return conditionQuestion; }
    public boolean isConditionAttendue()      { return conditionAttendue; }

    public void setLibelle(String libelle)            { this.libelle = libelle; }
    public void setType(TypeQuestion type)            { this.type = type; }
    public void setPosition(int position)             { this.position = position; }
    public void setCible(CibleQuestion cible)         { this.cible = cible; }
    public void setNatureSuggeree(SpNatureIntervention n) { this.natureSuggeree = n; }
    public void setConditionQuestion(SpQuestion q)    { this.conditionQuestion = q; }
    public void setConditionAttendue(boolean v)       { this.conditionAttendue = v; }
}

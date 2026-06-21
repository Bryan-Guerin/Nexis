package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sp_grade")
public class SpGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    /** Ordre d'affichage (comme l'index d'une enum). */
    @Column(name = "ordre", nullable = false)
    private int position;

    /** Taux horaire (€) de garde, base de la paie. */
    @Column(name = "taux_horaire", nullable = false)
    private BigDecimal tauxHoraire = BigDecimal.ZERO;

    /** Taux horaire (€) d'astreinte. */
    @Column(name = "taux_astreinte", nullable = false)
    private BigDecimal tauxAstreinte = BigDecimal.ZERO;

    /** Le porteur de ce grade peut valider les CRI (sergent et +). */
    @Column(name = "peut_valider_cri", nullable = false)
    private boolean peutValiderCri = false;

    protected SpGrade() {}

    public SpGrade(String code, String label) {
        this.code  = code;
        this.label = label;
    }

    public UUID getId()             { return id; }
    public String getCode()         { return code; }
    public String getLabel()        { return label; }
    public int getPosition()        { return position; }
    public BigDecimal getTauxHoraire() { return tauxHoraire; }
    public BigDecimal getTauxAstreinte() { return tauxAstreinte; }
    public boolean isPeutValiderCri() { return peutValiderCri; }

    public void setPosition(int position)         { this.position = position; }
    public void setTauxHoraire(BigDecimal taux)   { this.tauxHoraire = taux != null ? taux : BigDecimal.ZERO; }
    public void setTauxAstreinte(BigDecimal taux) { this.tauxAstreinte = taux != null ? taux : BigDecimal.ZERO; }
    public void setPeutValiderCri(boolean v)      { this.peutValiderCri = v; }
}

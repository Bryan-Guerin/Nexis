package com.bryan.nexis.sapeurs.datamodel;

import jakarta.persistence.*;
import java.util.UUID;

/** Nature d'intervention (INC, SAP, AVP, DIV, NRBC…), configurable. */
@Entity
@Table(name = "sp_nature_intervention")
public class SpNatureIntervention {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "ordre", nullable = false)
    private int position;

    /** Icône (emoji) repérant l'intervention sur la carte. Optionnel. */
    @Column(length = 16)
    private String icone;

    /** Image-icône optionnelle (remplace l'emoji si définie). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icone_image_id")
    private SpIcone iconeImage;

    protected SpNatureIntervention() {}

    public SpNatureIntervention(String code, String label) {
        this.code = code; this.label = label;
    }

    public UUID getId()      { return id; }
    public String getCode()  { return code; }
    public String getLabel() { return label; }
    public int getPosition() { return position; }
    public String getIcone() { return icone; }
    public SpIcone getIconeImage() { return iconeImage; }

    public void setPosition(int position) { this.position = position; }
    public void setIcone(String icone)    { this.icone = icone; }
    public void setIconeImage(SpIcone iconeImage) { this.iconeImage = iconeImage; }
}

package com.bryan.nexis.core.datamodel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Notation mensuelle d'un membre, commune à toutes les factions.
 * Référence le membre par sa faction + son identifiant (pas de FK inter-module).
 */
@Entity
@Table(name = "notation")
public class Notation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 10)
    private String faction;

    @Column(name = "membre_id", nullable = false)
    private UUID membreId;

    /** Mois concerné, au format AAAA-MM. */
    @Column(nullable = false, length = 7)
    private String mois;

    @Column(name = "comportement_discipline", nullable = false) private int comportementDiscipline;
    @Column(name = "competences_techniques", nullable = false)  private int competencesTechniques;
    @Column(name = "aptitude_physique", nullable = false)       private int aptitudePhysique;
    @Column(name = "initiative_autonomie", nullable = false)    private int initiativeAutonomie;
    @Column(name = "esprit_equipe", nullable = false)           private int espritEquipe;
    @Column(name = "respect_securite", nullable = false)        private int respectSecurite;

    @Column(columnDefinition = "TEXT") private String observations;
    @Column(columnDefinition = "TEXT") private String objectifs;

    @Column(length = 50) private String evaluateur;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe = Instant.now();

    protected Notation() {}

    public Notation(String faction, UUID membreId, String mois, int comportementDiscipline,
                    int competencesTechniques, int aptitudePhysique, int initiativeAutonomie,
                    int espritEquipe, int respectSecurite, String observations, String objectifs,
                    String evaluateur) {
        this.faction                = faction;
        this.membreId               = membreId;
        this.mois                   = mois;
        this.comportementDiscipline = comportementDiscipline;
        this.competencesTechniques  = competencesTechniques;
        this.aptitudePhysique       = aptitudePhysique;
        this.initiativeAutonomie    = initiativeAutonomie;
        this.espritEquipe           = espritEquipe;
        this.respectSecurite        = respectSecurite;
        this.observations           = observations;
        this.objectifs              = objectifs;
        this.evaluateur             = evaluateur;
    }

    public UUID getId()                     { return id; }
    public String getFaction()              { return faction; }
    public UUID getMembreId()               { return membreId; }
    public String getMois()                 { return mois; }
    public int getComportementDiscipline()  { return comportementDiscipline; }
    public int getCompetencesTechniques()   { return competencesTechniques; }
    public int getAptitudePhysique()        { return aptitudePhysique; }
    public int getInitiativeAutonomie()     { return initiativeAutonomie; }
    public int getEspritEquipe()            { return espritEquipe; }
    public int getRespectSecurite()         { return respectSecurite; }
    public String getObservations()         { return observations; }
    public String getObjectifs()            { return objectifs; }
    public String getEvaluateur()           { return evaluateur; }
    public Instant getCreeLe()              { return creeLe; }
}

package com.bryan.nexis.core.backend.dto;

import com.bryan.nexis.core.datamodel.Notation;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record NotationDto(
        UUID id,
        String mois,
        int comportementDiscipline,
        int competencesTechniques,
        int aptitudePhysique,
        int initiativeAutonomie,
        int espritEquipe,
        int respectSecurite,
        String observations,
        String objectifs,
        String evaluateur,
        Instant creeLe
) {
    public static NotationDto from(Notation n) {
        return new NotationDto(n.getId(), n.getMois(), n.getComportementDiscipline(), n.getCompetencesTechniques(),
                n.getAptitudePhysique(), n.getInitiativeAutonomie(), n.getEspritEquipe(), n.getRespectSecurite(),
                n.getObservations(), n.getObjectifs(), n.getEvaluateur(), n.getCreeLe());
    }
}

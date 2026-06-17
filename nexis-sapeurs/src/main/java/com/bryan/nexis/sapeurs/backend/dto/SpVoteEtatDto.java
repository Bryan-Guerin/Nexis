package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * État courant du vote "intervention de la semaine" pour un membre :
 * - semaineDate = lundi de la semaine des candidates (= semaine close précédente)
 * - candidates  = interventions clôturées éligibles + leur compte de votes
 * - monVote     = id de l'intervention pour laquelle le membre a voté (null sinon)
 * - gagnant     = candidate avec le plus de votes (ex-aequo : la plus récente)
 */
@Serdeable
public record SpVoteEtatDto(
        LocalDate semaineDate,
        List<Candidate> candidates,
        UUID monVote,
        Candidate gagnant
) {
    @Serdeable
    public record Candidate(
            UUID interventionId, String code, String motif,
            String natureLabel, String commune,
            int votes
    ) {}
}

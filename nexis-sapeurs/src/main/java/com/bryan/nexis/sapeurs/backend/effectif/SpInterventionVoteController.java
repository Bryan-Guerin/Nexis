package com.bryan.nexis.sapeurs.backend.effectif;

import com.bryan.nexis.sapeurs.backend.dto.SpVoteEtatDto;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;

import java.util.UUID;

/** Vote intervention de la semaine (1 vote / SP actif / semaine). */
@Controller("/api/sp/vote")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP")
public class SpInterventionVoteController {

    private final SpInterventionVoteService voteService;
    private final SpMembreService           membreService;

    public SpInterventionVoteController(SpInterventionVoteService voteService,
                                        SpMembreService membreService) {
        this.voteService   = voteService;
        this.membreService = membreService;
    }

    /** État courant : candidates de la semaine close, mon vote, gagnant provisoire. */
    @Get("/semaine-courante")
    SpVoteEtatDto etat(Authentication auth) {
        var me = tryGetMe(auth);
        return voteService.etat(me);
    }

    /** Voter pour une intervention. Remplace mon vote précédent de la semaine. */
    @Post("/{interventionId}")
    SpVoteEtatDto voter(UUID interventionId, Authentication auth) {
        var me = membreService.findByUsername(auth.getName());
        return voteService.voter(me.id(), interventionId);
    }

    /** Retirer mon vote de la semaine. */
    @Delete
    @Status(HttpStatus.OK)
    SpVoteEtatDto retirer(Authentication auth) {
        var me = membreService.findByUsername(auth.getName());
        return voteService.retirerVote(me.id());
    }

    private UUID tryGetMe(Authentication auth) {
        try {
            return membreService.findByUsername(auth.getName()).id();
        } catch (RuntimeException e) {
            return null;   // utilisateur sans fiche SP : peut consulter sans voter
        }
    }
}

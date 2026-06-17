package com.bryan.nexis.sapeurs.backend.effectif;

import com.bryan.nexis.sapeurs.backend.dto.SpMembreBadgeDto;
import com.bryan.nexis.sapeurs.backend.dto.SpProfilRpDto;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;

import java.util.UUID;

/** Profil RP (XP + niveau + badges) + déclencheurs d'évaluation. */
@Controller("/api/sp")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP")
public class SpRpController {

    private final SpRpService     service;
    private final SpMembreService membreService;

    public SpRpController(SpRpService service, SpMembreService membreService) {
        this.service       = service;
        this.membreService = membreService;
    }

    /** Profil RP d'un membre (visible par tous). */
    @Get("/membres/{id}/profil-rp")
    SpProfilRpDto profil(UUID id) {
        return service.getProfil(id);
    }

    /** Évalue tous les membres actifs et attribue les badges éligibles (admin). */
    @Post("/badges/eval")
    @Secured("ROLE_ADMIN_SP")
    EvalResult evalAll() {
        return new EvalResult(service.evalAll());
    }

    /** Marque un de mes badges comme découvert (clic sur la fiche). */
    @Put("/membres/me/badges/{badgeId}/decouvrir")
    SpMembreBadgeDto decouvrir(UUID badgeId, Authentication auth) {
        var me = membreService.findByUsername(auth.getName());
        return service.markDecouvert(me.id(), badgeId);
    }

    @io.micronaut.serde.annotation.Serdeable
    public record EvalResult(int attribues) {}
}

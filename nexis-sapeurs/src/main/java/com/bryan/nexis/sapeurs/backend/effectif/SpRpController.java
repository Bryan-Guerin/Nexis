package com.bryan.nexis.sapeurs.backend.effectif;

import com.bryan.nexis.sapeurs.backend.dto.SpProfilRpDto;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;

import java.util.UUID;

/** Profil RP (XP + niveau + badges) + déclencheurs d'évaluation. */
@Controller("/api/sp")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP")
public class SpRpController {

    private final SpRpService service;

    public SpRpController(SpRpService service) { this.service = service; }

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

    @io.micronaut.serde.annotation.Serdeable
    public record EvalResult(int attribues) {}
}

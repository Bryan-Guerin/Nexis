package com.bryan.nexis.sapeurs.backend.config;

import com.bryan.nexis.sapeurs.backend.dto.SetIconeRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpBrandingDto;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;

/** Branding de l'instance : lecture du logo pour tous les SP, modification réservée admin. */
@Controller("/api/sp/branding")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP")
public class SpBrandingController {

    private final SpBrandingService service;

    public SpBrandingController(SpBrandingService service) {
        this.service = service;
    }

    @Get
    SpBrandingDto get() {
        return new SpBrandingDto(service.getLogoIconeId());
    }

    /** Définit le logo (réutilise iconeImageId de SetIconeRequest ; null = efface). */
    @Put("/logo")
    @Secured("ROLE_ADMIN_SP")
    SpBrandingDto setLogo(@Body SetIconeRequest req) {
        return new SpBrandingDto(service.setLogo(req.iconeImageId()));
    }
}

package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.backend.dto.SpCriDto;
import com.bryan.nexis.sapeurs.backend.dto.UpdateCriRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.UUID;

@Controller("/api/sp")
@Secured("ROLE_SP")
public class SpCriController {

    private final SpCriService service;

    public SpCriController(SpCriService service) {
        this.service = service;
    }

    @Get("/interventions/{id}/cri")
    List<SpCriDto> list(UUID id) {
        return service.listForIntervention(id);
    }

    @Put("/cri/{criId}")
    SpCriDto update(UUID criId, @Body UpdateCriRequest req) {
        return service.update(criId, req.contenu());
    }

    @Put("/cri/{criId}/soumettre")
    SpCriDto soumettre(UUID criId) {
        return service.soumettre(criId);
    }

    @Put("/cri/{criId}/valider")
    @Secured("ROLE_ADMIN_SP")
    SpCriDto valider(UUID criId) {
        return service.valider(criId);
    }
}

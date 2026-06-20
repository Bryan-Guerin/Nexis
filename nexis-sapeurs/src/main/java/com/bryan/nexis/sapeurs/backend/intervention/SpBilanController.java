package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.backend.bilan.BilanSapContenu;
import com.bryan.nexis.sapeurs.backend.dto.CreateVictimeRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpBilanDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVictimeDto;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.UUID;

/**
 * Victimes + bilans d'une intervention. Lecture ouverte aux SP ; écriture réservée (côté service)
 * à un équipier d'un engin de l'intervention, comme la main courante / le CRI.
 */
@Controller("/api/sp")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP")
public class SpBilanController {

    private final SpBilanService service;

    public SpBilanController(SpBilanService service) {
        this.service = service;
    }

    @Get("/interventions/{interventionId}/victimes")
    List<SpVictimeDto> victimes(UUID interventionId) {
        return service.listVictimes(interventionId);
    }

    @Post("/interventions/{interventionId}/victimes")
    @Status(HttpStatus.CREATED)
    SpVictimeDto ajouterVictime(UUID interventionId, @Body CreateVictimeRequest req) {
        return service.ajouterVictime(interventionId, req.libelle());
    }

    @Get("/interventions/{interventionId}/bilans")
    List<SpBilanDto> bilans(UUID interventionId) {
        return service.listBilans(interventionId);
    }

    /** Enregistre (crée ou écrase) le bilan SAP d'une victime. */
    @Put("/victimes/{victimeId}/bilan-sap")
    SpBilanDto enregistrerBilanSap(UUID victimeId, @Body BilanSapContenu contenu) {
        return service.enregistrerBilanSap(victimeId, contenu);
    }
}

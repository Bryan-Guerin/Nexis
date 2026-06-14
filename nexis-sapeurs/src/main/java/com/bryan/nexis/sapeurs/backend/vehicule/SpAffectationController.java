package com.bryan.nexis.sapeurs.backend.vehicule;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import com.bryan.nexis.sapeurs.backend.dto.SpAffecterRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpVehiculeAffectationDto;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Controller("/api/sp")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP")
public class SpAffectationController {

    private final SpVehiculeAffectationService affectationService;

    public SpAffectationController(SpVehiculeAffectationService affectationService) {
        this.affectationService = affectationService;
    }

    @Get("/affectations")
    List<SpVehiculeAffectationDto> findActives() {
        return affectationService.findActives();
    }

    @Post("/affectations")
    @Status(HttpStatus.CREATED)
    SpVehiculeAffectationDto affecter(@Body SpAffecterRequest req) {
        return affectationService.affecter(req.vehiculeId(), req.membreId(), req.posteId(), req.debut());
    }

    @Put("/affectations/{id}/cloture")
    SpVehiculeAffectationDto cloturer(UUID id, @QueryValue Instant fin) {
        return affectationService.cloturer(id, fin);
    }

    /** Désaffecte tout le personnel embarqué de tous les véhicules. Renvoie le nombre désaffecté. */
    @Put("/affectations/desaffecter-tout")
    int desaffecterTout() {
        return affectationService.cloturerToutes(Instant.now());
    }
}

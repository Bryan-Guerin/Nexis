package com.bryan.nexis.gendarmerie.backend;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import com.bryan.nexis.gendarmerie.backend.dto.GnAffecterRequest;
import com.bryan.nexis.gendarmerie.backend.dto.GnVehiculeAffectationDto;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Controller("/api/gn")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_GN")
public class GnAffectationController {

    private final GnVehiculeAffectationService affectationService;

    public GnAffectationController(GnVehiculeAffectationService affectationService) {
        this.affectationService = affectationService;
    }

    @Get("/affectations")
    List<GnVehiculeAffectationDto> findActives() {
        return affectationService.findActives();
    }

    @Post("/affectations")
    @Status(HttpStatus.CREATED)
    GnVehiculeAffectationDto affecter(@Body GnAffecterRequest req) {
        return affectationService.affecter(req.vehiculeId(), req.membreId(), req.debut());
    }

    @Put("/affectations/{id}/cloture")
    GnVehiculeAffectationDto cloturer(UUID id, @QueryValue Instant fin) {
        return affectationService.cloturer(id, fin);
    }
}

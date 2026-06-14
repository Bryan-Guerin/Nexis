package com.bryan.nexis.gendarmerie.backend;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import com.bryan.nexis.gendarmerie.backend.dto.*;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller("/api/gn")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_GN")
public class GnVehiculeController {

    private final GnVehiculeTypeService typeService;
    private final GnVehiculeService     vehiculeService;
    private final GnVehiculeEtatService etatService;

    public GnVehiculeController(GnVehiculeTypeService typeService, GnVehiculeService vehiculeService, GnVehiculeEtatService etatService) {
        this.typeService     = typeService;
        this.vehiculeService = vehiculeService;
        this.etatService     = etatService;
    }

    @Get("/vehicules/types")
    List<GnVehiculeTypeDto> listTypes() {
        return typeService.listAll();
    }

    @Post("/vehicules/types")
    @Status(HttpStatus.CREATED)
    GnVehiculeTypeDto createType(@Body CreateGnVehiculeTypeRequest req) {
        return typeService.create(req.code(), req.label());
    }

    @Get("/vehicules/etats")
    List<GnVehiculeEtatDto> listEtats() {
        return etatService.listAll();
    }

    @Get("/vehicules")
    List<GnVehiculeDto> listVehicules(@QueryValue Optional<String> etat) {
        return etat.map(vehiculeService::listByEtatCode).orElseGet(vehiculeService::listAll);
    }

    @Post("/vehicules")
    @Status(HttpStatus.CREATED)
    GnVehiculeDto createVehicule(@Body CreateGnVehiculeRequest req) {
        return vehiculeService.create(req.typeId(), req.libelle(), req.immatriculation());
    }

    @Put("/vehicules/{id}/etat")
    GnVehiculeDto updateEtat(UUID id, @QueryValue UUID etatId) {
        return vehiculeService.updateEtat(id, etatId);
    }
}

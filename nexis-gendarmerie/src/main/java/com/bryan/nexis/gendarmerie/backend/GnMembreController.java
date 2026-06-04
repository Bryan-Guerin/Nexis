package com.bryan.nexis.gendarmerie.backend;

import com.bryan.nexis.core.backend.dto.PlanningDto;
import com.bryan.nexis.core.backend.dto.PlanningStatutDto;
import com.bryan.nexis.gendarmerie.backend.dto.CreateGnMembreRequest;
import com.bryan.nexis.gendarmerie.backend.dto.CreateGnPlanningRequest;
import com.bryan.nexis.gendarmerie.backend.dto.GnMembreDto;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller("/api/gn")
@Secured("ROLE_GN")
public class GnMembreController {

    private final GnMembreService         membreService;
    private final GnPlanningService       planningService;
    private final GnPlanningStatutService planningStatutService;

    public GnMembreController(GnMembreService membreService, GnPlanningService planningService,
                              GnPlanningStatutService planningStatutService) {
        this.membreService         = membreService;
        this.planningService       = planningService;
        this.planningStatutService = planningStatutService;
    }

    @Get("/membres/me")
    GnMembreDto getMe(Authentication auth) {
        return membreService.findByUsername(auth.getName());
    }

    @Get("/planning/me")
    List<PlanningDto> getMyPlanning(Authentication auth) {
        var me = membreService.findByUsername(auth.getName());
        return planningService.listByMembre(me.id());
    }

    @Post("/planning/me")
    @Status(HttpStatus.CREATED)
    PlanningDto createMyPlanning(Authentication auth, @Body CreateGnPlanningRequest req) {
        var me = membreService.findByUsername(auth.getName());
        return planningService.create(me.id(), req.statutId(), req.debut(), req.fin(), req.notes());
    }

    @Get("/membres")
    List<GnMembreDto> listMembres(@QueryValue Optional<Boolean> actif) {
        return actif.map(a -> a ? membreService.listActifs() : membreService.listAll())
                    .orElseGet(membreService::listAll);
    }

    /** Membres actuellement en service (plage GARDE couvrant maintenant). */
    @Get("/membres/en-service")
    List<UUID> membresEnService() {
        return planningService.membresEnService();
    }

    @Post("/membres")
    @Status(HttpStatus.CREATED)
    GnMembreDto createMembre(@Body CreateGnMembreRequest req) {
        return membreService.create(req.userId(), req.gradeId(), req.matricule());
    }

    @Get("/planning")
    List<PlanningDto> listAllPlanning() {
        return planningService.listAll();
    }

    @Get("/planning/statuts")
    List<PlanningStatutDto> listPlanningStatuts() {
        return planningStatutService.listAll();
    }

    @Get("/membres/{membreId}/planning")
    List<PlanningDto> listPlanning(UUID membreId) {
        return planningService.listByMembre(membreId);
    }

    @Post("/membres/{membreId}/planning")
    @Status(HttpStatus.CREATED)
    PlanningDto createPlanning(UUID membreId, @Body CreateGnPlanningRequest req) {
        return planningService.create(membreId, req.statutId(), req.debut(), req.fin(), req.notes());
    }
}

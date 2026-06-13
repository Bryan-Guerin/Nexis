package com.bryan.nexis.sapeurs.backend.effectif;

import com.bryan.nexis.core.backend.NotationService;
import com.bryan.nexis.core.backend.dto.NotationDto;
import com.bryan.nexis.core.backend.dto.PlanningDto;
import com.bryan.nexis.core.backend.dto.PlanningStatutDto;
import com.bryan.nexis.sapeurs.backend.dto.CreateSpMembreRequest;
import com.bryan.nexis.sapeurs.backend.dto.CreateSpPlanningRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpMembreDto;
import com.bryan.nexis.sapeurs.backend.dto.UpdateSpMembreRequest;
import com.bryan.nexis.sapeurs.backend.planning.SpPlanningService;
import com.bryan.nexis.sapeurs.backend.planning.SpPlanningStatutService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller("/api/sp")
@Secured("ROLE_SP")
public class SpMembreController {

    private final SpMembreService         membreService;
    private final SpPlanningService       planningService;
    private final SpPlanningStatutService planningStatutService;
    private final NotationService         notationService;

    public SpMembreController(SpMembreService membreService, SpPlanningService planningService,
                              SpPlanningStatutService planningStatutService, NotationService notationService) {
        this.membreService         = membreService;
        this.planningService       = planningService;
        this.planningStatutService = planningStatutService;
        this.notationService       = notationService;
    }

    /** Mes notations (l'effectif voit les siennes). */
    @Get("/membres/me/notations")
    List<NotationDto> mesNotations(Authentication auth) {
        var me = membreService.findByUsername(auth.getName());
        return notationService.list("SP", me.id());
    }

    // ── Moi-même ─────────────────────────────────────────────────────────────

    @Get("/membres/me")
    SpMembreDto getMe(Authentication auth) {
        return membreService.findByUsername(auth.getName());
    }

    @Get("/planning/me")
    List<PlanningDto> getMyPlanning(Authentication auth) {
        var me = membreService.findByUsername(auth.getName());
        return planningService.listByMembre(me.id());
    }

    @Post("/planning/me")
    @Status(HttpStatus.CREATED)
    PlanningDto createMyPlanning(Authentication auth, @Body CreateSpPlanningRequest req) {
        var me = membreService.findByUsername(auth.getName());
        // Auto-déclaration : début non rétroactif, arrondi au quart d'heure supérieur.
        return planningService.declareSelf(me.id(), req.statutId(), req.debut(), req.fin(), req.notes());
    }

    /** Termine la garde en cours du membre connecté (fin = maintenant). */
    @Put("/planning/me/terminer-garde")
    PlanningDto terminerMaGarde(Authentication auth) {
        var me = membreService.findByUsername(auth.getName());
        return planningService.terminerGardeEnCours(me.id());
    }

    /** Catégorie de service en cours du membre connecté (GARDE / ASTREINTE / null). */
    @Get("/planning/me/service-courant")
    ServiceCourant serviceCourant(Authentication auth) {
        var me = membreService.findByUsername(auth.getName());
        return new ServiceCourant(planningService.categorieCouranteService(me.id()));
    }

    /** Bascule l'astreinte en cours en garde (reste du créneau, min 1 h). */
    @Put("/planning/me/basculer/garde")
    PlanningDto basculerGarde(Authentication auth) {
        var me = membreService.findByUsername(auth.getName());
        return planningService.basculerVersGarde(me.id());
    }

    /** Bascule la garde en cours en astreinte (temps restant, sans prolonger). */
    @Put("/planning/me/basculer/astreinte")
    PlanningDto basculerAstreinte(Authentication auth) {
        var me = membreService.findByUsername(auth.getName());
        return planningService.basculerVersAstreinte(me.id());
    }

    @io.micronaut.serde.annotation.Serdeable
    public record ServiceCourant(String categorie) {}

    // ── Liste / création ─────────────────────────────────────────────────────

    @Get("/membres")
    List<SpMembreDto> listMembres(@QueryValue Optional<Boolean> actif) {
        return actif.filter(a -> a).map(a -> membreService.listActifs()).orElseGet(membreService::listAll);
    }

    @Get("/membres/grade")
    List<SpMembreDto> listMembresOrderByGrade() {
        return membreService.listAllOrderByGrade();
    }

    /** Membres actuellement en service (plage GARDE couvrant maintenant). */
    @Get("/membres/en-service")
    List<UUID> membresEnService() {
        return planningService.membresEnService();
    }

    @Post("/membres")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.CREATED)
    SpMembreDto createMembre(@Body CreateSpMembreRequest req) {
        return membreService.create(req.userId(), req.gradeId(), req.contrat(), req.numeroCasier(), req.nomComplet(), req.telephone());
    }

    /** Détail d'un effectif (chargé à la demande au clic). */
    @Get("/membres/{id}")
    SpMembreDto getMembre(UUID id) {
        return membreService.getById(id);
    }

    // ── Mise à jour partielle (admin) ─────────────────────────────────────────

    @Patch("/membres/{id}")
    @Secured("ROLE_ADMIN_SP")
    SpMembreDto updateMembre(UUID id, @Body UpdateSpMembreRequest req) {
        return membreService.update(id, req.gradeId(), req.contrat(), req.numeroCasier(), req.nomComplet(), req.telephone());
    }

    /** Radie (actif=false) ou réintègre un effectif. Désactive/réactive le compte lié. */
    @Put("/membres/{id}/actif")
    @Secured("ROLE_ADMIN_SP")
    SpMembreDto setActif(UUID id, @QueryValue boolean actif) {
        return membreService.setActif(id, actif);
    }

    // ── Planning ─────────────────────────────────────────────────────────────

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
    PlanningDto createPlanning(UUID membreId, @Body CreateSpPlanningRequest req) {
        return planningService.create(membreId, req.statutId(), req.debut(), req.fin(), req.notes());
    }

    // ── Gestion des gardes par le dispatch (démarrer / terminer pour autrui) ──────

    @Post("/planning/membres/{membreId}/prendre-garde")
    @Secured("ROLE_SP_DISPATCH")
    @Status(HttpStatus.CREATED)
    PlanningDto prendreGardePour(UUID membreId, @QueryValue(defaultValue = "4") int heures) {
        return planningService.prendreGardePour(membreId, heures);
    }

    @Put("/planning/membres/{membreId}/terminer-garde")
    @Secured("ROLE_SP_DISPATCH")
    PlanningDto terminerGardePour(UUID membreId) {
        return planningService.terminerGardeEnCours(membreId);
    }

    // ── Qualifications (fonctions du membre) ──────────────────────────────────

    @Post("/membres/{membreId}/qualifications/{fonctionId}")
    @Secured({"ROLE_SP_RH", "ROLE_ADMIN_SP"})
    @Status(HttpStatus.NO_CONTENT)
    void addQualification(UUID membreId, UUID fonctionId) {
        membreService.addQualification(membreId, fonctionId);
    }

    @Delete("/membres/{membreId}/qualifications/{fonctionId}")
    @Secured({"ROLE_SP_RH", "ROLE_ADMIN_SP"})
    @Status(HttpStatus.NO_CONTENT)
    void removeQualification(UUID membreId, UUID fonctionId) {
        membreService.removeQualification(membreId, fonctionId);
    }
}

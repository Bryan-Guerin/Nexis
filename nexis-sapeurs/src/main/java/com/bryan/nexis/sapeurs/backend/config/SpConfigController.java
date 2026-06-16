package com.bryan.nexis.sapeurs.backend.config;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import com.bryan.nexis.core.backend.dto.PlanningStatutDto;
import com.bryan.nexis.sapeurs.backend.dto.*;
import com.bryan.nexis.sapeurs.backend.effectif.SpFonctionService;
import com.bryan.nexis.sapeurs.backend.effectif.SpGradeService;
import com.bryan.nexis.sapeurs.backend.intervention.SpNatureInterventionService;
import com.bryan.nexis.sapeurs.backend.inventaire.SpCasierService;
import com.bryan.nexis.sapeurs.backend.inventaire.SpCentreService;
import com.bryan.nexis.sapeurs.backend.inventaire.SpObjetInventaireService;
import com.bryan.nexis.sapeurs.backend.planning.SpPlanningStatutService;
import com.bryan.nexis.sapeurs.backend.vehicule.SpVehiculeEtatService;
import com.bryan.nexis.sapeurs.backend.vehicule.SpVehiculeStatutService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.UUID;

@Controller("/api/sp")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_ADMIN_SP")
public class SpConfigController {

    private final SpGradeService             gradeService;
    private final SpFonctionService          fonctionService;
    private final SpVehiculeEtatService      etatService;
    private final SpCasierService            casierService;
    private final SpPlanningStatutService    planningStatutService;
    private final SpVehiculeStatutService    statutService;
    private final SpCentreService            centreService;
    private final SpNatureInterventionService natureService;
    private final SpObjetInventaireService   objetInventaireService;

    public SpConfigController(SpGradeService gradeService, SpFonctionService fonctionService,
                              SpVehiculeEtatService etatService, SpCasierService casierService,
                              SpPlanningStatutService planningStatutService,
                              SpVehiculeStatutService statutService, SpCentreService centreService,
                              SpNatureInterventionService natureService,
                              SpObjetInventaireService objetInventaireService) {
        this.gradeService          = gradeService;
        this.fonctionService       = fonctionService;
        this.etatService           = etatService;
        this.casierService         = casierService;
        this.planningStatutService = planningStatutService;
        this.statutService         = statutService;
        this.centreService         = centreService;
        this.natureService         = natureService;
        this.objetInventaireService = objetInventaireService;
    }

    // ── Grades ────────────────────────────────────────────────────────────────

    @Get("/grades")
    @Secured("ROLE_SP")
    List<SpGradeDto> listGrades() {
        return gradeService.listAll();
    }

    @Post("/grades")
    @Status(HttpStatus.CREATED)
    SpGradeDto createGrade(@Body CreateSpGradeRequest req) {
        return gradeService.create(req.code(), req.label());
    }

    @Put("/grades/order")
    @Status(HttpStatus.NO_CONTENT)
    void reorderGrades(@Body ReorderRequest req) {
        gradeService.reorder(req.ids());
    }

    @Delete("/grades/{id}")
    io.micronaut.http.HttpResponse<?> deleteGrade(UUID id) {
        try {
            gradeService.delete(id);
            return io.micronaut.http.HttpResponse.noContent();
        } catch (IllegalStateException e) {
            return io.micronaut.http.HttpResponse.badRequest(e.getMessage());
        }
    }

    // ── Fonctions ───────────────────────────────────────────────────────────────

    @Get("/fonctions")
    @Secured("ROLE_SP")
    List<SpFonctionDto> listFonctions() {
        return fonctionService.listAll();
    }

    @Post("/fonctions")
    @Status(HttpStatus.CREATED)
    SpFonctionDto createFonction(@Body CreateSpFonctionRequest req) {
        return fonctionService.create(req.code(), req.label());
    }

    @Put("/fonctions/order")
    @Status(HttpStatus.NO_CONTENT)
    void reorderFonctions(@Body ReorderRequest req) {
        fonctionService.reorder(req.ids());
    }

    /** Catégorie de la fonction (ordre de l'équipage au dispatch). */
    @Put("/fonctions/{id}/type")
    SpFonctionDto setFonctionType(UUID id, @Body SetTypeFonctionRequest req) {
        return fonctionService.updateType(id, req.type());
    }

    @Delete("/fonctions/{id}")
    io.micronaut.http.HttpResponse<?> deleteFonction(UUID id) {
        try {
            fonctionService.delete(id);
            return io.micronaut.http.HttpResponse.noContent();
        } catch (IllegalStateException e) {
            return io.micronaut.http.HttpResponse.badRequest(e.getMessage());
        }
    }

    // ── États véhicule (maître, système — lecture seule, non modifiables) ─────────

    @Get("/etats")
    @Secured("ROLE_SP")
    List<SpVehiculeEtatDto> listEtats() {
        return etatService.listAll();
    }

    // ── Statuts véhicule (RP, ordonnés, liés à un état) ───────────────────────────

    @Get("/statuts")
    @Secured("ROLE_SP")
    List<SpVehiculeStatutDto> listStatuts() {
        return statutService.listAll();
    }

    @Post("/statuts")
    @Status(HttpStatus.CREATED)
    SpVehiculeStatutDto createStatut(@Body CreateSpVehiculeStatutRequest req) {
        return statutService.create(req.code(), req.label(), req.couleur(), req.etatId(),
                Boolean.TRUE.equals(req.clotureIntervention()));
    }

    /** Bascule la case « clôture auto d'intervention » du statut. */
    @Put("/statuts/{id}/cloture-intervention")
    SpVehiculeStatutDto toggleClotureIntervention(UUID id) {
        return statutService.toggleClotureIntervention(id);
    }

    @Put("/statuts/order")
    @Status(HttpStatus.NO_CONTENT)
    void reorderStatuts(@Body ReorderRequest req) {
        statutService.reorder(req.ids());
    }

    @Put("/statuts/{id}/defaut")
    @Status(HttpStatus.NO_CONTENT)
    void setStatutDefaut(UUID id) {
        statutService.setDefault(id);
    }

    @Delete("/statuts/{id}")
    @Status(HttpStatus.NO_CONTENT)
    void deleteStatut(UUID id) {
        statutService.delete(id);
    }

    // ── Casiers ───────────────────────────────────────────────────────────────

    @Get("/casiers")
    @Secured("ROLE_SP")
    List<SpCasierDto> listCasiers() {
        return casierService.listAll();
    }

    @Post("/casiers")
    @Status(HttpStatus.CREATED)
    SpCasierDto createCasier(@Body CreateSpCasierRequest req) {
        return casierService.create(req.numero());
    }

    @Delete("/casiers/{id}")
    @Status(HttpStatus.NO_CONTENT)
    void deleteCasier(UUID id) {
        casierService.delete(id);
    }

    @Put("/casiers/order")
    @Status(HttpStatus.NO_CONTENT)
    void reorderCasiers(@Body ReorderRequest req) {
        casierService.reorder(req.ids());
    }

    // ── Statuts de planning ─────────────────────────────────────────────────────
    // (la lecture GET /planning/statuts est exposée côté ROLE_SP par SpMembreController)

    @Post("/planning/statuts")
    @Status(HttpStatus.CREATED)
    PlanningStatutDto createPlanningStatut(@Body CreateSpPlanningStatutRequest req) {
        return planningStatutService.create(req.code(), req.label(), req.couleur(), req.categorie());
    }

    @Put("/planning/statuts/order")
    @Status(HttpStatus.NO_CONTENT)
    void reorderPlanningStatuts(@Body ReorderRequest req) {
        planningStatutService.reorder(req.ids());
    }

    // ── Centres (casernes) ───────────────────────────────────────────────────────

    @Get("/centres")
    @Secured("ROLE_SP")
    List<SpCentreDto> listCentres() {
        return centreService.listAll();
    }

    @Post("/centres")
    @Status(HttpStatus.CREATED)
    SpCentreDto createCentre(@Body CreateSpCentreRequest req) {
        return centreService.create(req.code(), req.label());
    }

    @Delete("/centres/{id}")
    @Status(HttpStatus.NO_CONTENT)
    void deleteCentre(UUID id) {
        centreService.delete(id);
    }

    /** Définit les coordonnées jeu de la caserne (pour la carte). */
    @Put("/centres/{id}/coordonnees")
    SpCentreDto setCentreCoordonnees(UUID id, @Body SetCoordonneesRequest req) {
        return centreService.setCoordonnees(id, req.coordonnees());
    }

    @Put("/centres/order")
    @Status(HttpStatus.NO_CONTENT)
    void reorderCentres(@Body ReorderRequest req) {
        centreService.reorder(req.ids());
    }

    // ── Natures d'intervention ───────────────────────────────────────────────────

    @Get("/natures")
    @Secured("ROLE_SP")
    List<SpNatureInterventionDto> listNatures() {
        return natureService.listAll();
    }

    @Post("/natures")
    @Status(HttpStatus.CREATED)
    SpNatureInterventionDto createNature(@Body CreateSpNatureInterventionRequest req) {
        return natureService.create(req.code(), req.label());
    }

    @Put("/natures/order")
    @Status(HttpStatus.NO_CONTENT)
    void reorderNatures(@Body ReorderRequest req) {
        natureService.reorder(req.ids());
    }

    /** Icône (emoji) repérant la nature sur la carte. */
    @Put("/natures/{id}/icone")
    SpNatureInterventionDto setNatureIcone(UUID id, @Body SetIconeRequest req) {
        return natureService.setIcone(id, req.icone());
    }

    /** Supprime une nature (refusée si des interventions l'utilisent ; détachée des types). */
    @Delete("/natures/{id}")
    io.micronaut.http.HttpResponse<?> deleteNature(UUID id) {
        try {
            natureService.delete(id);
            return io.micronaut.http.HttpResponse.noContent();
        } catch (IllegalStateException e) {
            return io.micronaut.http.HttpResponse.badRequest(e.getMessage());
        }
    }

    // ── Objets d'inventaire (catalogue) ──────────────────────────────────────────

    @Get("/objets-inventaire")
    @Secured("ROLE_SP")
    List<SpObjetInventaireDto> listObjetsInventaire() {
        return objetInventaireService.listAll();
    }

    @Post("/objets-inventaire")
    @Status(HttpStatus.CREATED)
    SpObjetInventaireDto createObjetInventaire(@Body CreateSpObjetInventaireRequest req) {
        return objetInventaireService.create(req.code(), req.label());
    }

    @Delete("/objets-inventaire/{id}")
    @Status(HttpStatus.NO_CONTENT)
    void deleteObjetInventaire(UUID id) {
        objetInventaireService.delete(id);
    }

    @Put("/objets-inventaire/order")
    @Status(HttpStatus.NO_CONTENT)
    void reorderObjetsInventaire(@Body ReorderRequest req) {
        objetInventaireService.reorder(req.ids());
    }
}

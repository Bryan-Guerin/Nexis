package com.bryan.nexis.sapeurs.backend.config;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import com.bryan.nexis.core.backend.dto.PlanningStatutDto;
import com.bryan.nexis.sapeurs.backend.dto.*;
import com.bryan.nexis.sapeurs.backend.effectif.SpFonctionOrgaService;
import com.bryan.nexis.sapeurs.backend.effectif.SpFonctionService;
import com.bryan.nexis.sapeurs.backend.effectif.SpGradeService;
import com.bryan.nexis.sapeurs.backend.intervention.SpNatureInterventionService;
import com.bryan.nexis.sapeurs.backend.inventaire.SpCasierService;
import com.bryan.nexis.sapeurs.backend.inventaire.SpCentreService;
import com.bryan.nexis.sapeurs.backend.inventaire.SpHopitalService;
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
    private final SpFonctionOrgaService      fonctionOrgaService;
    private final SpVehiculeEtatService      etatService;
    private final SpCasierService            casierService;
    private final SpPlanningStatutService    planningStatutService;
    private final SpVehiculeStatutService    statutService;
    private final SpCentreService            centreService;
    private final SpHopitalService           hopitalService;
    private final SpNatureInterventionService natureService;
    private final SpObjetInventaireService   objetInventaireService;

    public SpConfigController(SpGradeService gradeService, SpFonctionService fonctionService,
                              SpFonctionOrgaService fonctionOrgaService,
                              SpVehiculeEtatService etatService, SpCasierService casierService,
                              SpPlanningStatutService planningStatutService,
                              SpVehiculeStatutService statutService, SpCentreService centreService,
                              SpHopitalService hopitalService,
                              SpNatureInterventionService natureService,
                              SpObjetInventaireService objetInventaireService) {
        this.gradeService          = gradeService;
        this.fonctionService       = fonctionService;
        this.fonctionOrgaService   = fonctionOrgaService;
        this.etatService           = etatService;
        this.casierService         = casierService;
        this.planningStatutService = planningStatutService;
        this.statutService         = statutService;
        this.centreService         = centreService;
        this.hopitalService        = hopitalService;
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

    @Put("/grades/{id}/valider-cri")
    SpGradeDto setPeutValiderCri(UUID id, @io.micronaut.http.annotation.QueryValue boolean peutValider) {
        return gradeService.updatePeutValiderCri(id, peutValider);
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

    // ── Fonctions organigramme (rôles caserne, arborescents) ──────────────────────

    @Get("/fonctions-orga")
    @Secured("ROLE_SP")
    List<SpFonctionOrgaDto> listFonctionsOrga() {
        return fonctionOrgaService.listAll();
    }

    @Post("/fonctions-orga")
    @Status(HttpStatus.CREATED)
    SpFonctionOrgaDto createFonctionOrga(@Body CreateSpFonctionOrgaRequest req) {
        return fonctionOrgaService.create(req.code(), req.label(), req.parentId(), req.icone(), req.iconeImageId());
    }

    @Put("/fonctions-orga/{id}")
    SpFonctionOrgaDto updateFonctionOrga(UUID id, @Body UpdateSpFonctionOrgaRequest req) {
        return fonctionOrgaService.update(id, req.label(), req.parentId(), req.icone(), req.iconeImageId());
    }

    @Put("/fonctions-orga/order")
    @Status(HttpStatus.NO_CONTENT)
    void reorderFonctionsOrga(@Body ReorderRequest req) {
        fonctionOrgaService.reorder(req.ids());
    }

    @Delete("/fonctions-orga/{id}")
    @Status(HttpStatus.NO_CONTENT)
    void deleteFonctionOrga(UUID id) {
        fonctionOrgaService.delete(id);
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

    /** Définit l'action carte branchée sur le statut (transport hôpital, sur place…). */
    @Put("/statuts/{id}/action-carte")
    SpVehiculeStatutDto setStatutActionCarte(UUID id, @Body SetActionCarteRequest req) {
        return statutService.setActionCarte(id, req.action());
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

    // ── Hôpitaux ─────────────────────────────────────────────────────────────────

    @Get("/hopitaux")
    @Secured("ROLE_SP")
    List<SpHopitalDto> listHopitaux() {
        return hopitalService.listAll();
    }

    @Post("/hopitaux")
    @Status(HttpStatus.CREATED)
    SpHopitalDto createHopital(@Body CreateSpHopitalRequest req) {
        return hopitalService.create(req.code(), req.label());
    }

    @Delete("/hopitaux/{id}")
    @Status(HttpStatus.NO_CONTENT)
    void deleteHopital(UUID id) {
        hopitalService.delete(id);
    }

    /** Définit les coordonnées jeu de l'hôpital (pour la carte). */
    @Put("/hopitaux/{id}/coordonnees")
    SpHopitalDto setHopitalCoordonnees(UUID id, @Body SetCoordonneesRequest req) {
        return hopitalService.setCoordonnees(id, req.coordonnees());
    }

    @Put("/hopitaux/order")
    @Status(HttpStatus.NO_CONTENT)
    void reorderHopitaux(@Body ReorderRequest req) {
        hopitalService.reorder(req.ids());
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

    /** Icône repérant la nature sur la carte : emoji + image optionnelle. */
    @Put("/natures/{id}/icone")
    SpNatureInterventionDto setNatureIcone(UUID id, @Body SetIconeRequest req) {
        return natureService.setIcone(id, req.icone(), req.iconeImageId());
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

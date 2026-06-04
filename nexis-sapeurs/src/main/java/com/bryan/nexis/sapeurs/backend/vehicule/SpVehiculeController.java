package com.bryan.nexis.sapeurs.backend.vehicule;

import com.bryan.nexis.sapeurs.backend.dto.*;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller("/api/sp")
@Secured("ROLE_SP")
public class SpVehiculeController {

    private final SpVehiculeTypeService        typeService;
    private final SpVehiculeService            vehiculeService;
    private final SpVehiculeEtatService        etatService;
    private final SpVehiculeStatutService      statutService;
    private final SpVehiculeAffectationService affectationService;

    public SpVehiculeController(SpVehiculeTypeService typeService, SpVehiculeService vehiculeService,
                                SpVehiculeEtatService etatService, SpVehiculeStatutService statutService,
                                SpVehiculeAffectationService affectationService) {
        this.typeService        = typeService;
        this.vehiculeService    = vehiculeService;
        this.etatService        = etatService;
        this.statutService      = statutService;
        this.affectationService = affectationService;
    }

    @Get("/vehicules/types")
    List<SpVehiculeTypeDto> listTypes() {
        return typeService.listAll();
    }

    @Post("/vehicules/types")
    @Status(HttpStatus.CREATED)
    SpVehiculeTypeDto createType(@Body CreateSpVehiculeTypeRequest req) {
        return typeService.create(req.code(), req.label());
    }

    @Get("/vehicules/types/{typeId}/postes")
    List<SpVehiculeTypePosteDto> listPostes(UUID typeId) {
        return typeService.listPostes(typeId);
    }

    @Post("/vehicules/types/{typeId}/postes")
    @Status(HttpStatus.CREATED)
    SpVehiculeTypePosteDto addPoste(UUID typeId, @Body CreateSpPosteRequest req) {
        return typeService.addPoste(typeId, req.fonctionId(), req.nbPlaces(), req.obligatoire());
    }

    @Put("/vehicules/postes/{posteId}/obligatoire")
    SpVehiculeTypePosteDto toggleObligatoire(UUID posteId) {
        return typeService.toggleObligatoire(posteId);
    }

    @Delete("/vehicules/postes/{posteId}")
    io.micronaut.http.HttpResponse<?> deletePoste(UUID posteId) {
        try {
            typeService.deletePoste(posteId);
            return io.micronaut.http.HttpResponse.noContent();
        } catch (IllegalStateException e) {
            return io.micronaut.http.HttpResponse.badRequest(e.getMessage());
        }
    }

    /** Tags du type ↔ natures d'intervention (proposition d'engins). */
    @Put("/vehicules/types/{typeId}/natures")
    SpVehiculeTypeDto setTypeNatures(UUID typeId, @Body SetTypeNaturesRequest req) {
        return typeService.setNatures(typeId, req.natureIds());
    }

    @Get("/vehicules/etats")
    List<SpVehiculeEtatDto> listEtats() {
        return etatService.listAll();
    }

    @Get("/vehicules/statuts")
    List<SpVehiculeStatutDto> listStatuts() {
        return statutService.listAll();
    }

    @Get("/vehicules")
    List<SpVehiculeDto> listVehicules(@QueryValue Optional<String> etat) {
        return etat.map(vehiculeService::listByEtatCode).orElseGet(vehiculeService::listAll);
    }

    /** Véhicules disponibles (+ armé + natures du type) — pour la proposition à la création d'intervention. */
    @Get("/vehicules/engageables")
    List<SpVehiculeEngageableDto> listEngageables() {
        return vehiculeService.listEngageables();
    }

    @Post("/vehicules")
    @Status(HttpStatus.CREATED)
    SpVehiculeDto createVehicule(@Body CreateSpVehiculeRequest req) {
        return vehiculeService.create(req.typeId(), req.libelle(), req.immatriculation(),
                req.centreId(), req.capaciteEau(), req.notes());
    }

    @Patch("/vehicules/{id}")
    SpVehiculeDto updateVehicule(UUID id, @Body UpdateSpVehiculeRequest req) {
        return vehiculeService.update(id, req.libelle(), req.immatriculation(),
                req.centreId(), req.capaciteEau(), req.notes());
    }

    /** Force l'état maître (système : maintenance, inventaire, indisponibilité…). */
    @Put("/vehicules/{id}/etat")
    SpVehiculeDto updateEtat(UUID id, @QueryValue UUID etatId) {
        return vehiculeService.updateEtat(id, etatId);
    }

    /** Bascule le statut RP (équipier affecté, transition avant uniquement). */
    @Put("/vehicules/{id}/statut")
    SpVehiculeDto updateStatut(UUID id, @QueryValue UUID statutId) {
        return vehiculeService.updateStatut(id, statutId);
    }

    /** Fait sonner l'équipage actif du véhicule. Retourne le nombre de personnes bipées. */
    @Post("/vehicules/{id}/bip")
    int bip(UUID id) {
        return affectationService.bip(id);
    }
}

package com.bryan.nexis.sapeurs.backend.effectif;

import com.bryan.nexis.sapeurs.backend.dto.CreateSpBadgeRequest;
import com.bryan.nexis.sapeurs.backend.dto.ReorderRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpBadgeDto;
import com.bryan.nexis.sapeurs.backend.dto.SpMembreBadgeDto;
import com.bryan.nexis.sapeurs.backend.dto.UpdateSpBadgeRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.UUID;

/**
 * Catalogue de badges (CRUD admin) + lecture des badges d'un membre (tous rôles SP).
 * Évaluation automatique = service séparé (étape suivante).
 */
@Controller("/api/sp")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP")
public class SpBadgeController {

    private final SpBadgeService service;

    public SpBadgeController(SpBadgeService service) {
        this.service = service;
    }

    // ── Catalogue (lecture pour tous, écriture admin) ─────────────────────────

    @Get("/badges")
    List<SpBadgeDto> listCatalog() {
        return service.listCatalog();
    }

    @Post("/badges")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.CREATED)
    SpBadgeDto create(@Body CreateSpBadgeRequest req) {
        return service.create(req.code(), req.label(), req.icone(), req.iconeImageId(), req.description(),
                req.typeCondition(), req.natureId(), req.typeFonction(), req.fonctionOrgaId(),
                req.seuil(), req.xpReward());
    }

    @Put("/badges/{id}")
    @Secured("ROLE_ADMIN_SP")
    SpBadgeDto update(UUID id, @Body UpdateSpBadgeRequest req) {
        return service.update(id, req.label(), req.icone(), req.iconeImageId(), req.description(),
                req.typeCondition(), req.natureId(), req.typeFonction(), req.fonctionOrgaId(),
                req.seuil(), req.xpReward());
    }

    @Put("/badges/order")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.NO_CONTENT)
    void reorder(@Body ReorderRequest req) {
        service.reorder(req.ids());
    }

    @Delete("/badges/{id}")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.NO_CONTENT)
    void delete(UUID id) {
        service.delete(id);
    }

    // ── Badges obtenus par un membre (vue publique : tous SP) ─────────────────

    @Get("/membres/{id}/badges")
    List<SpMembreBadgeDto> listForMembre(UUID id) {
        return service.listForMembre(id);
    }
}

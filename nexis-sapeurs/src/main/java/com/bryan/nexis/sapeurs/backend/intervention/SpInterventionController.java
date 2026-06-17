package com.bryan.nexis.sapeurs.backend.intervention;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import com.bryan.nexis.core.backend.dto.JournalEntryDto;
import com.bryan.nexis.sapeurs.backend.dto.*;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller("/api/sp")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP")
public class SpInterventionController {

    private final SpInterventionService service;

    public SpInterventionController(SpInterventionService service) {
        this.service = service;
    }

    @Get("/interventions")
    List<SpInterventionDto> list(@QueryValue Optional<Boolean> enCours) {
        return enCours.filter(Boolean::booleanValue)
                .map(b -> service.listEnCours())
                .orElseGet(service::listAll);
    }

    @Get("/interventions/{id}")
    SpInterventionDto get(UUID id) {
        return service.getById(id);
    }

    @Post("/interventions")
    @Status(HttpStatus.CREATED)
    SpInterventionDto create(@Body CreateSpInterventionRequest req) {
        return service.create(req);
    }

    /** Aperçu des effectifs sur poste non obligatoire qui seraient désaffectés au déclenchement. */
    @Post("/interventions/preview-desaffectation")
    List<DesaffectationPreviewDto> previewDesaffectation(@Body AddEnginsRequest req) {
        return service.previewDesaffectationNonObligatoire(req.vehiculeIds());
    }

    /** Main courante de l'intervention (événements reliés à son code). */
    @Get("/interventions/{id}/journal")
    List<JournalEntryDto> journal(UUID id) {
        return service.mainCourante(id);
    }

    /** Ajoute une note de main courante (équipier de l'intervention). */
    @Post("/interventions/{id}/journal")
    @Status(HttpStatus.CREATED)
    void addJournal(UUID id, @Body AddMainCouranteRequest req) {
        service.addMainCourante(id, req.message());
    }

    @Patch("/interventions/{id}")
    SpInterventionDto update(UUID id, @Body UpdateSpInterventionRequest req) {
        return service.update(id, req);
    }

    /** Statut des renforts GN / VINCI — éditable par tous. */
    @Put("/interventions/{id}/renfort")
    SpInterventionDto updateRenfort(UUID id, @Body UpdateRenfortRequest req) {
        return service.updateRenfort(id, req.renfortGn(), req.renfortVinci());
    }

    @Post("/interventions/{id}/engins")
    SpInterventionDto addEngins(UUID id, @Body AddEnginsRequest req) {
        return service.addEngins(id, req.vehiculeIds());
    }

    @Delete("/interventions/{id}/engins/{vehiculeId}")
    SpInterventionDto retirerEngin(UUID id, UUID vehiculeId) {
        return service.retirerEngin(id, vehiculeId);
    }

    /** Clôture forcée — réservée au dispatcher (et à l'admin SP par héritage). */
    @Put("/interventions/{id}/cloture")
    @Secured("ROLE_SP_DISPATCH")
    SpInterventionDto cloturer(UUID id) {
        return service.cloturer(id);
    }
}

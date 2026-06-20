package com.bryan.nexis.sapeurs.backend.intervention;

import com.bryan.nexis.sapeurs.backend.dto.CreateSpQuestionRequest;
import com.bryan.nexis.sapeurs.backend.dto.ReorderRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpQuestionDto;
import com.bryan.nexis.sapeurs.backend.dto.UpdateSpQuestionRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.UUID;

/**
 * Questionnaire guidé du dispatcher. Lecture réservée dispatch + admin (le menu guidé n'est
 * proposé qu'aux dispatchers) ; configuration réservée à l'admin SP.
 */
@Controller("/api/sp")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP")
public class SpQuestionController {

    private final SpQuestionService service;

    public SpQuestionController(SpQuestionService service) {
        this.service = service;
    }

    @Get("/questions")
    @Secured({"ROLE_SP_DISPATCH", "ROLE_ADMIN_SP"})
    List<SpQuestionDto> list() {
        return service.listAll();
    }

    @Post("/questions")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.CREATED)
    SpQuestionDto create(@Body CreateSpQuestionRequest req) {
        return service.create(req.libelle(), req.type(), req.cible(), req.natureSuggereeId(),
                req.conditionQuestionId(), req.conditionAttendue(), req.recoVehiculeTypeId(), req.recoParUnite());
    }

    @Put("/questions/order")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.NO_CONTENT)
    void reorder(@Body ReorderRequest req) {
        service.reorder(req.ids());
    }

    @Put("/questions/{id}")
    @Secured("ROLE_ADMIN_SP")
    SpQuestionDto update(UUID id, @Body UpdateSpQuestionRequest req) {
        return service.update(id, req.libelle(), req.type(), req.cible(), req.natureSuggereeId(),
                req.conditionQuestionId(), req.conditionAttendue(), req.recoVehiculeTypeId(), req.recoParUnite());
    }

    @Delete("/questions/{id}")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.NO_CONTENT)
    void delete(UUID id) {
        service.delete(id);
    }
}

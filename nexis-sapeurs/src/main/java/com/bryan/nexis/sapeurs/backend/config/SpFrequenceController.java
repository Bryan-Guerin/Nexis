package com.bryan.nexis.sapeurs.backend.config;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import com.bryan.nexis.sapeurs.backend.dto.CreateFrequenceRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpFrequenceRadioDto;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.UUID;

/** Fréquences radio : lecture pour tous les SP, gestion réservée aux admins SP. */
@Controller("/api/sp")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP")
public class SpFrequenceController {

    private final SpFrequenceRadioService service;

    public SpFrequenceController(SpFrequenceRadioService service) {
        this.service = service;
    }

    @Get("/frequences")
    List<SpFrequenceRadioDto> list() {
        return service.listAll();
    }

    @Post("/frequences")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.CREATED)
    SpFrequenceRadioDto create(@Body CreateFrequenceRequest req) {
        return service.create(req.description(), req.frequence());
    }

    @Delete("/frequences/{id}")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.NO_CONTENT)
    void delete(UUID id) {
        service.delete(id);
    }
}

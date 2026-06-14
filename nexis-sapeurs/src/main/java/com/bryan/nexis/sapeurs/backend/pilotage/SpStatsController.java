package com.bryan.nexis.sapeurs.backend.pilotage;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import com.bryan.nexis.sapeurs.backend.dto.SpInterventionStatsDto;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;

@Controller("/api/sp/stats")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP")
public class SpStatsController {

    private final SpStatsService service;

    public SpStatsController(SpStatsService service) {
        this.service = service;
    }

    @Get("/interventions")
    SpInterventionStatsDto interventions() {
        return service.interventions();
    }
}

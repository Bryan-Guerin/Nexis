package com.bryan.nexis.sapeurs.backend.pilotage;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import com.bryan.nexis.sapeurs.backend.dto.HeatmapPointDto;
import com.bryan.nexis.sapeurs.backend.dto.SpInterventionStatsDto;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    /** Heatmap des interventions (par coordonnées). Tous les filtres optionnels. */
    @Get("/heatmap")
    List<HeatmapPointDto> heatmap(@QueryValue(value = "from",     defaultValue = "") String from,
                                  @QueryValue(value = "to",       defaultValue = "") String to,
                                  @QueryValue(value = "natureId", defaultValue = "") String natureId) {
        Instant fi = from.isEmpty()     ? null : Instant.parse(from);
        Instant ti = to.isEmpty()       ? null : Instant.parse(to);
        UUID    n  = natureId.isEmpty() ? null : UUID.fromString(natureId);
        return service.heatmap(fi, ti, n);
    }
}

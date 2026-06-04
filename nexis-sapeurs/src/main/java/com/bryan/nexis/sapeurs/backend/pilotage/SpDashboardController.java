package com.bryan.nexis.sapeurs.backend.pilotage;

import com.bryan.nexis.sapeurs.backend.dto.SpDashboardDto;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;

@Secured("ROLE_SP")
@Controller("/api/sp/dashboard")
public class SpDashboardController {

    private final SpDashboardService dashboardService;

    public SpDashboardController(SpDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Get
    public SpDashboardDto stats() {
        return dashboardService.load();
    }
}

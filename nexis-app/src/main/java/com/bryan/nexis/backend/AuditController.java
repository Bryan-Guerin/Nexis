package com.bryan.nexis.backend;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import com.bryan.nexis.core.backend.JournalService;
import com.bryan.nexis.core.backend.dto.JournalEntryDto;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.Optional;

/** Audit système : journal complet (toutes factions + connexions), réservé à l'admin. */
@Controller("/api/admin")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SYSTEM")
public class AuditController {

    private final JournalService journalService;

    public AuditController(JournalService journalService) {
        this.journalService = journalService;
    }

    @Get("/journal")
    List<JournalEntryDto> audit(@QueryValue Optional<Integer> limit) {
        // Audit = uniquement les événements système/admin (connexions, gestion users/rôles).
        // Les événements opérationnels SP/GN sont consultables via les mains courantes.
        return journalService.recentSysteme(limit.orElse(300));
    }
}

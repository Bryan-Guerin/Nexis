package com.bryan.nexis.sapeurs.backend.pilotage;

import com.bryan.nexis.core.backend.JournalService;
import com.bryan.nexis.core.backend.dto.JournalEntryDto;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.Optional;

/** Main courante SP : timeline des événements opérationnels de la faction. */
@Controller("/api/sp")
@Secured("ROLE_SP")
public class SpJournalController {

    private final JournalService journalService;

    public SpJournalController(JournalService journalService) {
        this.journalService = journalService;
    }

    @Get("/journal")
    List<JournalEntryDto> mainCourante(@QueryValue Optional<Integer> limit) {
        return journalService.recentByFaction("SP", limit.orElse(200));
    }
}

package com.bryan.nexis.sapeurs.backend.pilotage;

import com.bryan.nexis.core.backend.JournalService;
import com.bryan.nexis.core.backend.dto.JournalEntryDto;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Main courante SP : timeline des événements opérationnels de la faction. */
@Controller("/api/sp")
@Secured("ROLE_SP")
public class SpJournalController {

    private final JournalService journalService;
    private final SpActeurNommage nommage;

    public SpJournalController(JournalService journalService, SpActeurNommage nommage) {
        this.journalService = journalService;
        this.nommage = nommage;
    }

    /**
     * Main courante. Avec {@code from} + {@code to} : événements du jour demandé
     * (pagination par jour, évite de tout charger). Sinon : derniers {@code limit}.
     */
    @Get("/journal")
    List<JournalEntryDto> mainCourante(@QueryValue Optional<Instant> from,
                                       @QueryValue Optional<Instant> to,
                                       @QueryValue Optional<Integer> limit) {
        var base = (from.isPresent() && to.isPresent())
                ? journalService.byFactionBetween("SP", from.get(), to.get())
                : journalService.recentByFaction("SP", limit.orElse(200));
        return nommage.enrichir(base);
    }
}

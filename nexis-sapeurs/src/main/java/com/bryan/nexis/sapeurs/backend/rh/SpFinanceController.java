package com.bryan.nexis.sapeurs.backend.rh;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import com.bryan.nexis.sapeurs.backend.dto.FinanceRequests.CreateCategorieRequest;
import com.bryan.nexis.sapeurs.backend.dto.FinanceRequests.CreateMouvementRequest;
import com.bryan.nexis.sapeurs.backend.dto.FinanceRequests.UpdateCompteRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpFinanceDto;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.utils.SecurityService;

import java.util.UUID;

/** Trésorerie SP — réservé au référent RH (et à l'admin SP). Visible d'eux seuls. */
@Controller("/api/sp/rh/finance")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured({"ROLE_SP_RH", "ROLE_ADMIN_SP"})
public class SpFinanceController {

    private final SpFinanceService financeService;
    private final SecurityService  securityService;

    public SpFinanceController(SpFinanceService financeService, SecurityService securityService) {
        this.financeService  = financeService;
        this.securityService = securityService;
    }

    @Get
    SpFinanceDto resume() {
        return financeService.resume();
    }

    @Put("/compte")
    SpFinanceDto updateCompte(@Body UpdateCompteRequest req) {
        return financeService.updateCompte(req.libelle(), req.soldeInitial());
    }

    @Post("/categories")
    SpFinanceDto addCategorie(@Body CreateCategorieRequest req) {
        return financeService.addCategorie(req.libelle());
    }

    @Delete("/categories/{id}")
    SpFinanceDto deleteCategorie(UUID id) {
        return financeService.deleteCategorie(id);
    }

    @Post("/mouvements")
    SpFinanceDto addMouvement(@Body CreateMouvementRequest req) {
        return financeService.addMouvement(req.type(), req.montant(), req.libelle(), req.date(),
                req.categorieId(), securityService.username().orElse(null));
    }
}

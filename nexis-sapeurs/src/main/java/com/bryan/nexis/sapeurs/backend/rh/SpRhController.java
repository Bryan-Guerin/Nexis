package com.bryan.nexis.sapeurs.backend.rh;

import com.bryan.nexis.core.backend.NotationService;
import com.bryan.nexis.core.backend.dto.CreateNotationRequest;
import com.bryan.nexis.core.backend.dto.NotationDto;
import com.bryan.nexis.sapeurs.backend.dto.CreateRelanceRequest;
import com.bryan.nexis.sapeurs.backend.dto.CreateSanctionRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpGradeDto;
import com.bryan.nexis.sapeurs.backend.dto.SpPaieSemaineDto;
import com.bryan.nexis.sapeurs.backend.dto.SpRelanceDto;
import com.bryan.nexis.sapeurs.backend.dto.SpSanctionDto;
import com.bryan.nexis.sapeurs.backend.effectif.SpGradeService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.utils.SecurityService;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Module RH / paie / notation — réservé au référent RH (et à l'admin SP par héritage). */
@Controller("/api/sp/rh")
@Secured({"ROLE_SP_RH", "ROLE_ADMIN_SP"})
public class SpRhController {

    private final SpPaieService    paieService;
    private final SpGradeService   gradeService;
    private final NotationService  notationService;
    private final SpRelanceService relanceService;
    private final SpSanctionService sanctionService;
    private final SecurityService  securityService;

    public SpRhController(SpPaieService paieService, SpGradeService gradeService,
                          NotationService notationService, SpRelanceService relanceService,
                          SpSanctionService sanctionService, SecurityService securityService) {
        this.paieService     = paieService;
        this.gradeService    = gradeService;
        this.notationService = notationService;
        this.relanceService  = relanceService;
        this.sanctionService = sanctionService;
        this.securityService = securityService;
    }

    /** Paie de la semaine contenant la date donnée (défaut : semaine en cours). */
    @Get("/paie")
    SpPaieSemaineDto paie(@QueryValue Optional<String> lundi) {
        return paieService.semaine(lundi.map(LocalDate::parse).orElse(null));
    }

    /** Grades avec leur taux horaire (pour gérer la base de paie). */
    @Get("/grades")
    List<SpGradeDto> grades() {
        return gradeService.listAll();
    }

    /** Définit les taux (garde + astreinte) d'un grade. */
    @Put("/grades/{id}/taux")
    SpGradeDto setTaux(@PathVariable UUID id, @Body TauxRequest req) {
        return gradeService.updateTaux(id, req.tauxHoraire(), req.tauxAstreinte());
    }

    @Serdeable
    public record TauxRequest(BigDecimal tauxHoraire, BigDecimal tauxAstreinte) {}

    // ── Notations mensuelles ──────────────────────────────────────────────────

    @Get("/membres/{membreId}/notations")
    List<NotationDto> notations(@PathVariable UUID membreId) {
        return notationService.list("SP", membreId);
    }

    @Post("/membres/{membreId}/notations")
    @Status(HttpStatus.CREATED)
    NotationDto noter(@PathVariable UUID membreId, @Body CreateNotationRequest req) {
        return notationService.create("SP", membreId, req, securityService.username().orElse(null));
    }

    // ── Relances (rappels manuels : recyclages, compétences…) ─────────────────

    /** Toutes les relances ouvertes — vue d'ensemble pour repérer les recyclages à prévoir. */
    @Get("/relances/ouvertes")
    List<SpRelanceDto> relancesOuvertes() {
        return relanceService.ouvertes();
    }

    @Get("/membres/{membreId}/relances")
    List<SpRelanceDto> relances(@PathVariable UUID membreId) {
        return relanceService.listForMembre(membreId);
    }

    @Post("/membres/{membreId}/relances")
    @Status(HttpStatus.CREATED)
    SpRelanceDto creerRelance(@PathVariable UUID membreId, @Body CreateRelanceRequest req) {
        return relanceService.create(membreId, req.texte(), req.echeance());
    }

    @Put("/relances/{id}/fait")
    SpRelanceDto relanceFaite(@PathVariable UUID id) {
        return relanceService.marquerFait(id);
    }

    @Delete("/relances/{id}")
    @Status(HttpStatus.NO_CONTENT)
    void supprimerRelance(@PathVariable UUID id) {
        relanceService.supprimer(id);
    }

    // ── Sanctions disciplinaires ──────────────────────────────────────────────

    @Get("/membres/{membreId}/sanctions")
    List<SpSanctionDto> sanctions(@PathVariable UUID membreId) {
        return sanctionService.listForMembre(membreId);
    }

    @Post("/membres/{membreId}/sanctions")
    @Status(HttpStatus.CREATED)
    SpSanctionDto creerSanction(@PathVariable UUID membreId, @Body CreateSanctionRequest req) {
        return sanctionService.create(membreId, req.type(), req.motif(), req.dateSanction());
    }

    @Delete("/sanctions/{id}")
    @Status(HttpStatus.NO_CONTENT)
    void supprimerSanction(@PathVariable UUID id) {
        sanctionService.supprimer(id);
    }
}

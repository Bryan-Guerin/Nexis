package com.bryan.nexis.sapeurs.backend.evenement;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import com.bryan.nexis.sapeurs.backend.dto.CreateEvenementRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpEvenementDto;
import com.bryan.nexis.sapeurs.backend.dto.SpEvenementReponsesDto;
import com.bryan.nexis.sapeurs.backend.effectif.SpMembreService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;

import java.util.List;
import java.util.UUID;

@Controller("/api/sp")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP")
public class SpEvenementController {

    private final SpEvenementService service;
    private final SpMembreService membreService;

    public SpEvenementController(SpEvenementService service, SpMembreService membreService) {
        this.service = service;
        this.membreService = membreService;
    }

    /** Événements à venir / en cours (tableau de bord) — avec la réponse de l'utilisateur. */
    @Get("/evenements")
    List<SpEvenementDto> listAVenir(Authentication auth) {
        return service.listAVenir(resolveMembreId(auth));
    }

    /** Tous les événements (gestion admin), du plus récent au plus ancien. */
    @Get("/evenements/tous")
    @Secured("ROLE_ADMIN_SP")
    List<SpEvenementDto> listTous() {
        return service.listTous(null);
    }

    /** Bilan présents / absents déclarés d'un événement (admin). */
    @Get("/evenements/{id}/reponses")
    @Secured("ROLE_ADMIN_SP")
    SpEvenementReponsesDto reponses(UUID id) {
        return service.reponses(id);
    }

    @Post("/evenements")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.CREATED)
    SpEvenementDto create(@Body CreateEvenementRequest req, Authentication auth) {
        return service.create(req.titre(), req.texte(), req.date(), auth.getName());
    }

    @Delete("/evenements/{id}")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.NO_CONTENT)
    void delete(UUID id) {
        service.delete(id);
    }

    /** Déclaration de présence de l'effectif courant (present=true/false). */
    @Put("/evenements/{id}/reponse")
    @Status(HttpStatus.NO_CONTENT)
    void repondre(UUID id, @QueryValue boolean present, Authentication auth) {
        var me = membreService.findByUsername(auth.getName());
        service.repondre(id, me.id(), present);
    }

    /** Résout le membre SP de l'utilisateur courant, ou null (ex. admin sans fiche effectif). */
    private UUID resolveMembreId(Authentication auth) {
        try {
            return membreService.findByUsername(auth.getName()).id();
        } catch (Exception e) {
            return null;
        }
    }
}

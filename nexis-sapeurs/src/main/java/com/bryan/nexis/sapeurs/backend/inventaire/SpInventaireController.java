package com.bryan.nexis.sapeurs.backend.inventaire;

import com.bryan.nexis.sapeurs.backend.dto.CreateInventaireItemRequest;
import com.bryan.nexis.sapeurs.backend.dto.CreateVerificationRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpInventaireItemDto;
import com.bryan.nexis.sapeurs.backend.dto.SpVerificationDto;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.UUID;

@Controller("/api/sp")
@Secured("ROLE_SP")
public class SpInventaireController {

    private final SpInventaireService service;

    public SpInventaireController(SpInventaireService service) {
        this.service = service;
    }

    // ── Modèle d'inventaire (par type) ───────────────────────────────────────
    @Get("/vehicules/types/{typeId}/inventaire")
    List<SpInventaireItemDto> listItems(UUID typeId) {
        return service.listItems(typeId);
    }

    @Post("/vehicules/types/{typeId}/inventaire")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.CREATED)
    SpInventaireItemDto addItem(UUID typeId, @Body CreateInventaireItemRequest req) {
        return service.addItem(typeId, req.objetId(), req.quantite());
    }

    @Delete("/inventaire/{itemId}")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.NO_CONTENT)
    void deleteItem(UUID itemId) {
        service.deleteItem(itemId);
    }

    // ── Vérifications d'un véhicule (checklist + historique) ──────────────────
    @Get("/vehicules/{id}/verifications")
    List<SpVerificationDto> listVerifications(UUID id) {
        return service.listVerifications(id);
    }

    @Post("/vehicules/{id}/verifications")
    @Status(HttpStatus.CREATED)
    SpVerificationDto verifier(UUID id, @Body CreateVerificationRequest req) {
        return service.createVerification(id, req);
    }
}

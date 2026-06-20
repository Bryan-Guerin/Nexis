package com.bryan.nexis.sapeurs.backend.icone;

import com.bryan.nexis.sapeurs.backend.dto.SpIconeDto;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.UUID;

/**
 * Bibliothèque d'images-icônes : lecture pour tous les SP (affichage), upload/suppression admin.
 * Le contenu est servi par {@link #contenu(UUID)} (utilisé en {@code <img src>} côté front).
 */
@Controller("/api/sp/icones")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured("ROLE_SP")
public class SpIconeController {

    private final SpIconeService service;

    public SpIconeController(SpIconeService service) {
        this.service = service;
    }

    @Get
    List<SpIconeDto> list() {
        return service.list();
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.CREATED)
    SpIconeDto upload(@Part("nom") String nom, @Part("fichier") CompletedFileUpload fichier) {
        return service.upload(nom, fichier);
    }

    @Delete("/{id}")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.NO_CONTENT)
    void delete(UUID id) {
        service.delete(id);
    }

    /**
     * Sert le binaire de l'icône. Cache long (le contenu est immuable par id) et durcissement
     * SVG (CSP sandbox + nosniff) : un éventuel script embarqué dans un SVG ne s'exécute pas.
     */
    @Get("/{id}/contenu")
    HttpResponse<byte[]> contenu(UUID id) {
        var icone = service.get(id);
        return HttpResponse.ok(icone.getContenu())
                .contentType(icone.getContentType())
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox");
    }
}

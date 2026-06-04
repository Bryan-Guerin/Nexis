package com.bryan.nexis.sapeurs.backend.document;

import com.bryan.nexis.sapeurs.backend.dto.CreateDocumentCategorieRequest;
import com.bryan.nexis.sapeurs.backend.dto.SpDocumentCategorieView;
import com.bryan.nexis.sapeurs.backend.dto.SpDocumentDto;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.security.annotation.Secured;

import java.util.List;
import java.util.UUID;

@Controller("/api/sp/documents")
@Secured("ROLE_SP")
public class SpDocumentController {

    private final SpDocumentService service;

    public SpDocumentController(SpDocumentService service) {
        this.service = service;
    }

    @Get
    List<SpDocumentCategorieView> list() {
        return service.list();
    }

    @Post("/categories")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.CREATED)
    SpDocumentCategorieView createCategorie(@Body CreateDocumentCategorieRequest req) {
        return service.createCategorie(req.nom());
    }

    @Delete("/categories/{id}")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.NO_CONTENT)
    void deleteCategorie(UUID id) {
        service.deleteCategorie(id);
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.CREATED)
    SpDocumentDto upload(@Part("categorieId") UUID categorieId, @Part("nom") String nom,
                         @Part("fichier") CompletedFileUpload fichier) {
        return service.upload(categorieId, nom, fichier);
    }

    @Delete("/{id}")
    @Secured("ROLE_ADMIN_SP")
    @Status(HttpStatus.NO_CONTENT)
    void deleteDocument(UUID id) {
        service.deleteDocument(id);
    }

    /** Téléchargement / affichage du PDF (inline). */
    @Get("/{id}/fichier")
    @Produces(MediaType.APPLICATION_PDF)
    HttpResponse<byte[]> fichier(UUID id) {
        var doc = service.get(id);
        return HttpResponse.ok(doc.getContenu())
                .contentType(doc.getContentType())
                .header("Content-Disposition", "inline; filename=\"" + doc.getNom().replace("\"", "") + ".pdf\"");
    }
}

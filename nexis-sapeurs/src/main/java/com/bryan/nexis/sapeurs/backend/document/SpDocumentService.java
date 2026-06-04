package com.bryan.nexis.sapeurs.backend.document;

import com.bryan.nexis.sapeurs.backend.dto.SpDocumentCategorieView;
import com.bryan.nexis.sapeurs.backend.dto.SpDocumentDto;
import com.bryan.nexis.sapeurs.datamodel.SpDocument;
import com.bryan.nexis.sapeurs.datamodel.SpDocumentCategorie;
import com.bryan.nexis.sapeurs.datarepository.SpDocumentCategorieRepository;
import com.bryan.nexis.sapeurs.datarepository.SpDocumentRepository;
import io.micronaut.data.model.Sort;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Singleton
public class SpDocumentService {

    private static final Sort BY_POSITION = Sort.of(Sort.Order.asc("position"));
    private static final long MAX_BYTES = 25L * 1024 * 1024;   // 25 Mo
    private static final String PDF = "application/pdf";

    private final SpDocumentCategorieRepository categorieRepo;
    private final SpDocumentRepository          documentRepo;
    private final SecurityService               securityService;

    public SpDocumentService(SpDocumentCategorieRepository categorieRepo, SpDocumentRepository documentRepo,
                             SecurityService securityService) {
        this.categorieRepo   = categorieRepo;
        this.documentRepo    = documentRepo;
        this.securityService = securityService;
    }

    @Transactional
    public List<SpDocumentCategorieView> list() {
        return categorieRepo.findAll(BY_POSITION).stream()
                .map(c -> new SpDocumentCategorieView(c.getId(), c.getNom(), c.getPosition(),
                        documentRepo.metaByCategorie(c.getId())))
                .toList();
    }

    @Transactional
    public SpDocumentCategorieView createCategorie(String nom) {
        var c = new SpDocumentCategorie(nom);
        c.setPosition((int) categorieRepo.count());
        var saved = categorieRepo.save(c);
        return new SpDocumentCategorieView(saved.getId(), saved.getNom(), saved.getPosition(), List.of());
    }

    @Transactional
    public void deleteCategorie(UUID id) {
        categorieRepo.deleteById(id);   // cascade sur les documents
    }

    @Transactional
    public SpDocumentDto upload(UUID categorieId, String nom, CompletedFileUpload fichier) {
        var categorie = categorieRepo.findById(categorieId)
                .orElseThrow(() -> new NoSuchElementException("Catégorie introuvable : " + categorieId));

        String ct = fichier.getContentType().map(Object::toString).orElse("");
        String fn = fichier.getFilename() != null ? fichier.getFilename().toLowerCase() : "";
        if (!PDF.equalsIgnoreCase(ct) && !fn.endsWith(".pdf")) {
            throw new IllegalArgumentException("Seuls les fichiers PDF sont acceptés.");
        }
        if (fichier.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Fichier trop volumineux (max 25 Mo).");
        }

        byte[] bytes;
        try {
            bytes = fichier.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Lecture du fichier impossible.", e);
        }

        String libelle = (nom != null && !nom.isBlank()) ? nom.trim()
                : fn.endsWith(".pdf") ? fichier.getFilename().substring(0, fichier.getFilename().length() - 4)
                : "Document";
        var doc = new SpDocument(categorie, libelle, PDF, bytes.length, bytes,
                securityService.username().orElse(null));
        return SpDocumentDto.from(documentRepo.save(doc));
    }

    /** Document complet (avec contenu) pour le téléchargement. */
    @Transactional
    public SpDocument get(UUID id) {
        return documentRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Document introuvable : " + id));
    }

    @Transactional
    public void deleteDocument(UUID id) {
        documentRepo.deleteById(id);
    }
}

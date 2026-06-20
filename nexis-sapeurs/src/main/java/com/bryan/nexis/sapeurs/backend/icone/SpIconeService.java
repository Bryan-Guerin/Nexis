package com.bryan.nexis.sapeurs.backend.icone;

import com.bryan.nexis.sapeurs.backend.dto.SpIconeDto;
import com.bryan.nexis.sapeurs.datamodel.SpIcone;
import com.bryan.nexis.sapeurs.datarepository.SpIconeRepository;
import io.micronaut.data.model.Sort;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Bibliothèque d'images-icônes (upload + lecture), stockées en base. Calqué sur SpDocumentService. */
@Singleton
public class SpIconeService {

    private static final Sort BY_NOM = Sort.of(Sort.Order.asc("nom"));
    private static final long MAX_BYTES = 1024L * 1024;   // 1 Mo — une icône reste petite

    /** Types image acceptés → content-type normalisé servi ensuite. */
    private static final Map<String, String> EXT_TO_TYPE = Map.of(
            "png",  "image/png",
            "jpg",  "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "gif",  "image/gif",
            "svg",  "image/svg+xml");

    private final SpIconeRepository repo;
    private final SecurityService   securityService;

    public SpIconeService(SpIconeRepository repo, SecurityService securityService) {
        this.repo            = repo;
        this.securityService = securityService;
    }

    @Transactional
    public List<SpIconeDto> list() {
        return repo.findAll(BY_NOM).stream().map(SpIconeDto::from).toList();
    }

    @Transactional
    public SpIconeDto upload(String nom, CompletedFileUpload fichier) {
        String fn   = fichier.getFilename() != null ? fichier.getFilename() : "";
        String ext  = fn.contains(".") ? fn.substring(fn.lastIndexOf('.') + 1).toLowerCase() : "";
        String ctIn = fichier.getContentType().map(Object::toString).orElse("").toLowerCase();

        // Type normalisé : on fait confiance à l'extension (le content-type du navigateur
        // peut être octet-stream) ; on accepte aussi un content-type image déjà valide.
        String contentType = EXT_TO_TYPE.getOrDefault(ext,
                EXT_TO_TYPE.containsValue(ctIn) ? ctIn : null);
        if (contentType == null) {
            throw new IllegalArgumentException("Format d'image non supporté (png, jpg, webp, gif, svg).");
        }
        if (fichier.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Image trop volumineuse (max 1 Mo).");
        }

        byte[] bytes;
        try {
            bytes = fichier.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Lecture de l'image impossible.", e);
        }

        String libelle = (nom != null && !nom.isBlank()) ? nom.trim()
                : !ext.isBlank() && fn.length() > ext.length() + 1 ? fn.substring(0, fn.length() - ext.length() - 1)
                : "Icône";
        var icone = new SpIcone(libelle, contentType, bytes.length, bytes,
                securityService.username().orElse(null));
        return SpIconeDto.from(repo.save(icone));
    }

    /** Icône complète (avec binaire) pour le service du contenu. */
    @Transactional
    public SpIcone get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Icône introuvable : " + id));
    }

    @Transactional
    public void delete(UUID id) {
        repo.deleteById(id);
    }
}

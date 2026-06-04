package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.backend.dto.SpDocumentDto;
import com.bryan.nexis.sapeurs.datamodel.SpDocument;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpDocumentRepository extends JpaRepository<SpDocument, UUID> {
    /** Métadonnées (sans le contenu binaire) d'une catégorie, via constructeur JPQL. */
    @Query("""
        SELECT new com.bryan.nexis.sapeurs.backend.dto.SpDocumentDto(
            d.id, d.nom, d.contentType, d.taille, d.creeLe, d.creePar)
        FROM SpDocument d WHERE d.categorie.id = :categorieId ORDER BY d.creeLe DESC""")
    List<SpDocumentDto> metaByCategorie(UUID categorieId);
}

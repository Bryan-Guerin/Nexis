package com.bryan.nexis.sapeurs.datarepository;

import com.bryan.nexis.sapeurs.datamodel.SpDocumentCategorie;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.UUID;

@Repository
public interface SpDocumentCategorieRepository extends JpaRepository<SpDocumentCategorie, UUID> {}

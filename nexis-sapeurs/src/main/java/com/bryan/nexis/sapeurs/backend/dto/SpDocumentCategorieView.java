package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

/** Catégorie de documents avec ses documents (métadonnées). */
@Serdeable
public record SpDocumentCategorieView(UUID id, String nom, int position, List<SpDocumentDto> documents) {}

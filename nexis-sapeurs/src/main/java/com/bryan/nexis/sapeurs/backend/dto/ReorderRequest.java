package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

/** Liste ordonnée d'identifiants pour réordonner une catégorie de configuration. */
@Serdeable
public record ReorderRequest(List<UUID> ids) {}

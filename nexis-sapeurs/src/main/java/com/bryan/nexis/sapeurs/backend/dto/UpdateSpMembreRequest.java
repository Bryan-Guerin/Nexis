package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/**
 * Requête de mise à jour partielle d'un membre SP (admin uniquement).
 * Tous les champs sont optionnels (null = pas de modification).
 */
@Serdeable
public record UpdateSpMembreRequest(
        UUID gradeId,        // null = inchangé
        String contrat,      // null = inchangé ; sinon "SPP" ou "SPV"
        Integer numeroCasier,// null = inchangé
        String nomComplet    // null = inchangé
) {}

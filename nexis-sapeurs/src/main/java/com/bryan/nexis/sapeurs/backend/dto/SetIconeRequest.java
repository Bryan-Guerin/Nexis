package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Icône d'une entité : emoji (null/vide pour effacer) + image optionnelle (id sp_icone, null = aucune). */
@Serdeable
public record SetIconeRequest(String icone, UUID iconeImageId) {}

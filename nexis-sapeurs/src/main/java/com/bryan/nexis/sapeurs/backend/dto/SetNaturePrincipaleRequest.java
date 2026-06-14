package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Étoile la nature principale d'un type de véhicule (null = retire l'étoile). */
@Serdeable
public record SetNaturePrincipaleRequest(UUID natureId) {}

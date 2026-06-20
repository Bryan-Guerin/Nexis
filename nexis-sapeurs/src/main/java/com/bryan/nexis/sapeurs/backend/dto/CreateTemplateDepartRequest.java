package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Ajout d'une ligne de lot de départ : un type de véhicule + quantité (+ note/image optionnelles). */
@Serdeable
public record CreateTemplateDepartRequest(UUID vehiculeTypeId, int quantite,
                                          String description, UUID iconeImageId) {}

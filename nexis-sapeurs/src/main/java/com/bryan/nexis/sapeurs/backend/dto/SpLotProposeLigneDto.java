package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Ligne du lot recommandé à la création : un type de véhicule + la quantité requise (max des déclencheurs). */
@Serdeable
public record SpLotProposeLigneDto(UUID vehiculeTypeId, String typeCode, String typeLabel, int quantite) {}

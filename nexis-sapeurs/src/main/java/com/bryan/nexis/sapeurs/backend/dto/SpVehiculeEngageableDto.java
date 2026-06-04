package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

/** Véhicule disponible à l'engagement, avec son état « armé » et les natures de son type. */
@Serdeable
public record SpVehiculeEngageableDto(UUID vehiculeId, String libelle, String typeCode, UUID typeId,
                                      boolean arme, List<UUID> natureIds) {}

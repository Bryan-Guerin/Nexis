package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record UpdateSpFonctionOrgaRequest(String label, UUID parentId, String icone, UUID iconeImageId) {}

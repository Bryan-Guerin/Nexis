package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

@Serdeable
public record SetFonctionsOrgaRequest(List<UUID> fonctionOrgaIds) {}

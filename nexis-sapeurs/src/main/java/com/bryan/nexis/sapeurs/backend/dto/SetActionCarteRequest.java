package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.StatutActionCarte;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SetActionCarteRequest(StatutActionCarte action) {}

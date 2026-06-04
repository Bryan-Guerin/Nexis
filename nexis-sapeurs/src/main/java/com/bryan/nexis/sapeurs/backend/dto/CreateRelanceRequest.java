package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDate;

@Serdeable
public record CreateRelanceRequest(String texte, @Nullable LocalDate echeance) {}

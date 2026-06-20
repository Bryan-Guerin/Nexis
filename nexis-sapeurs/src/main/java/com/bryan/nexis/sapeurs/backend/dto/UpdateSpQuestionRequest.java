package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record UpdateSpQuestionRequest(String libelle, String type, String cible,
                                      UUID natureSuggereeId, UUID conditionQuestionId,
                                      Boolean conditionAttendue) {}

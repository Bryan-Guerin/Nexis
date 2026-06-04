package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record CreateVerificationRequest(List<Ligne> lignes) {

    @Serdeable
    public record Ligne(String libelle, int quantiteAttendue, int quantitePresente) {}
}

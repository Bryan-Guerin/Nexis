package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpVerification;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Serdeable
public record SpVerificationDto(UUID id, Instant creeLe, String par, boolean conforme, List<Ligne> lignes) {

    @Serdeable
    public record Ligne(String libelle, int quantiteAttendue, int quantitePresente, boolean conforme) {}

    public static SpVerificationDto from(SpVerification v) {
        var lignes = v.getLignes().stream()
                .map(l -> new Ligne(l.getLibelle(), l.getQuantiteAttendue(), l.getQuantitePresente(), l.isConforme()))
                .toList();
        return new SpVerificationDto(v.getId(), v.getCreeLe(), v.getPar(), v.isConforme(), lignes);
    }
}

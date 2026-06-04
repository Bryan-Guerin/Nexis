package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpRelance;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Serdeable
public record SpRelanceDto(UUID id, UUID membreId, String matricule, String texte, LocalDate echeance,
                           String statut, String creePar, Instant creeLe, String faitPar) {
    public static SpRelanceDto from(SpRelance r) {
        return new SpRelanceDto(r.getId(), r.getMembre().getId(), r.getMembre().getMatricule(),
                r.getTexte(), r.getEcheance(), r.getStatut(), r.getCreePar(), r.getCreeLe(), r.getFaitPar());
    }
}

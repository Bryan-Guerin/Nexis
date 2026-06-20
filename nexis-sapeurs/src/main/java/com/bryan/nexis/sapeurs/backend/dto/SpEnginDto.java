package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpVehicule;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Résumé d'un engin engagé sur une intervention (statut RP : Déclenché → … → Disponible). */
@Serdeable
public record SpEnginDto(UUID vehiculeId, String libelle, String typeCode,
                         String typeIcone, UUID typeIconeImageId,
                         String etatLabel, String etatCouleur, UUID statutId, int statutPosition) {

    public static SpEnginDto from(SpVehicule v) {
        return new SpEnginDto(v.getId(), v.getLibelle(), v.getType().getCode(),
                v.getType().getIcone(),
                v.getType().getIconeImage() != null ? v.getType().getIconeImage().getId() : null,
                v.getStatut().getLabel(), v.getStatut().getCouleur(),
                v.getStatut().getId(), v.getStatut().getPosition());
    }
}

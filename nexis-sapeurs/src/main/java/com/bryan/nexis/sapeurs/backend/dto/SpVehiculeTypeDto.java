package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpNatureIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpVehiculeType;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

@Serdeable
public record SpVehiculeTypeDto(UUID id, String code, String label, String icone, UUID iconeImageId,
                                int capaciteVictime, List<UUID> natureIds, NaturePrincipale naturePrincipale) {

    /** Nature principale (catégorie de regroupement dispatch) ; null si non définie. */
    @Serdeable
    public record NaturePrincipale(UUID id, String code, String label, int position) {}

    public static SpVehiculeTypeDto from(SpVehiculeType t) {
        var np = t.getNaturePrincipale();
        return new SpVehiculeTypeDto(t.getId(), t.getCode(), t.getLabel(), t.getIcone(),
                t.getIconeImage() != null ? t.getIconeImage().getId() : null,
                t.getCapaciteVictime(),
                t.getNatures().stream().map(SpNatureIntervention::getId).toList(),
                np == null ? null : new NaturePrincipale(np.getId(), np.getCode(), np.getLabel(), np.getPosition()));
    }
}

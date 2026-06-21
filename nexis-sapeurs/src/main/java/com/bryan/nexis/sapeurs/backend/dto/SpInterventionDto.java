package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.core.backend.dto.JournalEntryDto;
import com.bryan.nexis.sapeurs.datamodel.SpIntervention;
import com.bryan.nexis.sapeurs.datamodel.SpInterventionEngin;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Serdeable
public record SpInterventionDto(
        UUID id,
        String code,
        String motif,
        SpNatureInterventionDto nature,
        String requerant,
        String telephone,
        String observation,
        String commune,
        String coordonnees,
        Instant debut,
        Instant fin,
        boolean enCours,
        String creePar,
        Integer nbVictimes,
        boolean incendie,
        boolean vehiculeImplique,
        String renfortGn,
        String renfortVinci,
        List<SpEnginDto> engins,
        List<EnginHistoDto> enginsHisto,
        List<JournalEntryDto> dernieresLignes,
        /** EN_COURS | ATTENTE_CRI | ATTENTE_VALIDATION | CLOSE (derive de fin + statuts CRI). */
        String statutCloture
) {
    /** Engin historisé (texte) + son équipage figé, pour les interventions clôturées. */
    @Serdeable
    public record EnginHistoDto(String libelle, String typeCode, List<EquipierDto> equipage) {
        public static EnginHistoDto from(SpInterventionEngin e) {
            return new EnginHistoDto(e.getLibelle(), e.getTypeCode(),
                    e.getEquipage().stream()
                        .map(eq -> new EquipierDto(eq.getMatricule(), eq.getNom(), eq.getGrade(), eq.getPoste()))
                        .toList());
        }
    }

    @Serdeable
    public record EquipierDto(String matricule, String nom, String grade, String poste) {}

    public static SpInterventionDto from(SpIntervention i) {
        return from(i, List.of(), null);
    }

    public static SpInterventionDto from(SpIntervention i, List<JournalEntryDto> dernieresLignes) {
        return from(i, dernieresLignes, null);
    }

    public static SpInterventionDto from(SpIntervention i, List<JournalEntryDto> dernieresLignes, String statutCloture) {
        return new SpInterventionDto(
                i.getId(),
                i.getCode(),
                i.getMotif(),
                SpNatureInterventionDto.from(i.getNature()),
                i.getRequerant(),
                i.getTelephone(),
                i.getObservation(),
                i.getCommune(),
                i.getCoordonnees(),
                i.getDebut(),
                i.getFin(),
                i.getFin() == null,
                i.getCreePar(),
                i.getNbVictimes(),
                i.isIncendie(),
                i.isVehiculeImplique(),
                i.getRenfortGn(),
                i.getRenfortVinci(),
                i.getEngins().stream().map(SpEnginDto::from).toList(),
                i.getEnginsHisto().stream().map(EnginHistoDto::from).toList(),
                dernieresLignes,
                statutCloture
        );
    }
}

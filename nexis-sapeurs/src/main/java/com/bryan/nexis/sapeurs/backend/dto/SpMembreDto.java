package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.SpMembre;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Serdeable
public record SpMembreDto(
        UUID id,
        UUID userId,
        String username,
        UUID gradeId,
        String gradeCode,
        String grade,
        String matricule,
        String nomComplet,
        String telephone,
        String contrat,
        int numeroCasier,
        boolean actif,
        Instant dateIntegration,
        Instant dateDernierePromotion,
        List<QualificationDto> qualifications
) {
    /** Une habilitation : la fonction + sa date de délivrance et son délivreur. */
    @Serdeable
    public record QualificationDto(UUID fonctionId, String fonctionLabel, Instant dateDelivrance, String delivrePar) {}

    public static SpMembreDto from(SpMembre m) {
        var quals = m.getQualifications().stream()
                .map(q -> new QualificationDto(q.getFonction().getId(), q.getFonction().getLabel(),
                        q.getDateDelivrance(), q.getDelivrePar()))
                .sorted(Comparator.comparing(QualificationDto::fonctionLabel))
                .toList();
        return new SpMembreDto(
                m.getId(),
                m.getUser().getId(),
                m.getUser().getUsername(),
                m.getGrade().getId(),
                m.getGrade().getCode(),
                m.getGrade().getLabel(),
                m.getMatricule(),
                m.getNomComplet(),
                m.getTelephone(),
                m.getContrat(),
                m.getNumeroCasier(),
                m.isActif(),
                m.getDateIntegration(),
                m.getDateDernierePromotion(),
                quals
        );
    }
}

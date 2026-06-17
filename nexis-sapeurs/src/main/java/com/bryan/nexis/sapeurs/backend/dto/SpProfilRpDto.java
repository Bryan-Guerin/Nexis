package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Profil RP d'un membre : XP cumulé, niveau atteint, progression vers le niveau
 * suivant, compteurs (interventions, heures de garde, ancienneté…) et badges obtenus.
 */
@Serdeable
public record SpProfilRpDto(
        int xp,
        int niveau,
        int xpNiveauActuel,        // seuil XP du niveau atteint
        int xpNiveauSuivant,       // seuil XP du niveau suivant (Integer.MAX_VALUE si dernier)
        int progressionPct,        // 0..100 dans le niveau actuel
        Compteurs compteurs,
        List<SpMembreBadgeDto> badges
) {
    @Serdeable
    public record Compteurs(
            int interventions,
            int heuresGarde,
            int joursService,
            int joursGrade,
            int qualifications
    ) {}
}

package com.bryan.nexis.sapeurs.backend.dto;

import com.bryan.nexis.sapeurs.datamodel.BadgeCondition;
import com.bryan.nexis.sapeurs.datamodel.SpBadge;
import com.bryan.nexis.sapeurs.datamodel.SpMembreBadge;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/** Badge obtenu par un membre (vue publique : visible par tous). */
@Serdeable
public record SpMembreBadgeDto(
        UUID badgeId, String code, String label, String icone, UUID iconeImageId, String description,
        String condition, int xpReward, Instant obtenuLe, boolean decouvert) {

    public static SpMembreBadgeDto from(SpMembreBadge mb) {
        var b = mb.getBadge();
        return new SpMembreBadgeDto(
                b.getId(), b.getCode(), b.getLabel(), b.getIcone(),
                b.getIconeImage() != null ? b.getIconeImage().getId() : null,
                b.getDescription(),
                conditionText(b), b.getXpReward(), mb.getObtenuLe(), mb.isDecouvert());
    }

    /** Texte lisible de la condition d'obtention (repli de tooltip sans description). */
    private static String conditionText(SpBadge b) {
        String base = switch (b.getTypeCondition()) {
            case INTER_COUNT        -> "Interventions";
            case INTER_NATURE_COUNT -> "Interventions « " + label(b.getNature() != null ? b.getNature().getLabel() : null) + " »";
            case INTER_TYPE_FONCTION_COUNT -> "Interventions en « " + label(b.getTypeFonction() != null ? b.getTypeFonction().getLabel() : null) + " »";
            case INTER_SEMAINE_COUNT -> "Interventions de la semaine";
            case GARDE_HEURES       -> "Heures de garde";
            case SERVICE_JOURS      -> "Jours d'ancienneté";
            case GRADE_JOURS        -> "Jours dans le grade";
            case QUALIF_COUNT       -> "Qualifications";
            case QUALIF_TYPE_COUNT  -> "Qualifications « " + label(b.getTypeFonction() != null ? b.getTypeFonction().getLabel() : null) + " »";
            case FONCTION_ORGA      -> "Membre de « " + label(b.getFonctionOrga() != null ? b.getFonctionOrga().getLabel() : null) + " »";
        };
        String seuil = b.getTypeCondition() == BadgeCondition.FONCTION_ORGA ? "" : " · seuil " + b.getSeuil();
        return base + seuil + " · +" + b.getXpReward() + " XP";
    }

    private static String label(String s) { return s != null ? s : "?"; }
}

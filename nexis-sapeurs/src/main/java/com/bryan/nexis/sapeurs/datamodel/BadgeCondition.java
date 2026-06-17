package com.bryan.nexis.sapeurs.datamodel;

/**
 * Type de condition d'obtention d'un badge.
 * Le service d'évaluation scanne le compteur correspondant et compare au seuil.
 */
public enum BadgeCondition {
    /** Nombre total d'interventions auxquelles le membre a participé (engins armés). */
    INTER_COUNT,
    /** Nombre d'interventions d'une nature spécifique (natureId requis). */
    INTER_NATURE_COUNT,
    /** Heures de garde cumulées (issue du planning). */
    GARDE_HEURES,
    /** Jours d'ancienneté depuis l'intégration. */
    SERVICE_JOURS,
    /** Jours dans le grade courant. */
    GRADE_JOURS
}

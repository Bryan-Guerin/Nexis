package com.bryan.nexis.sapeurs.datamodel;

/** Champ d'intervention prérempli par la réponse à une question guidée. */
public enum CibleQuestion {
    /** Aucun préremplissage (question d'aiguillage : sert surtout à suggérer une nature). */
    AUCUNE,
    /** Coche « incendie » si la réponse est oui. */
    INCENDIE,
    /** Renseigne le nombre de victimes (réponse numérique). */
    NB_VICTIMES,
    /** Coche « véhicule impliqué » si la réponse est oui. */
    VEHICULE_IMPLIQUE
}

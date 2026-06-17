package com.bryan.nexis.sapeurs.datamodel;

/**
 * Action « carte » portée par un statut RP (configurable). Permet de brancher un
 * comportement sur la sémantique du statut sans dépendre de son nom/position.
 */
public enum StatutActionCarte {
    /** Défaut : aucun tracé carte — l'engin ne bouge pas (ex. « déclenché », à quai). */
    AUCUNE,
    /** En route : trait caserne → intervention (le 🚒 se déplace, ETA). */
    EN_ROUTE,
    /** Engin garé à sa destination courante (hôpital si transport en cours, sinon intervention). */
    SUR_PLACE,
    /** Transport vers un hôpital : ouvre le choix d'hôpital ; trait intervention → hôpital. */
    TRANSPORT_HOPITAL,
    /** Retour : trait (hôpital/intervention) → caserne. */
    RETOUR_CASERNE,
    /** Direction dépanneur (réservé, sans effet carte pour le moment). */
    DEPANNEUR
}

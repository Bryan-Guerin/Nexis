package com.bryan.nexis.core.datamodel;

/**
 * Catégorie transverse d'un statut de planning, partagée par toutes les factions
 * (GN, SP, dépanneurs…). C'est elle qui porte le sens métier commun : un membre
 * est « en service » s'il a une plage de catégorie {@link #GARDE} couvrant l'instant courant.
 *
 * <p>Chaque module définit ensuite ses propres statuts (référentiel configurable)
 * en les rattachant à l'une de ces catégories.</p>
 */
public enum TypeService {
    GARDE,
    ASTREINTE,
    AUTRE
}

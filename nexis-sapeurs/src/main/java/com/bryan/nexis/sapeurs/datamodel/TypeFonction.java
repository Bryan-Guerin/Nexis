package com.bryan.nexis.sapeurs.datamodel;

/**
 * Catégorie d'une fonction, qui pilote l'ordre d'affichage de l'équipage au dispatch.
 * L'ordre des constantes = l'ordre d'affichage (Chef d'agrès en premier, Équipier en dernier).
 */
public enum TypeFonction {
    CHEF_AGRES("Chef d'agrès"),
    CONDUCTEUR("Conducteur"),
    CHEF_EQUIPE("Chef d'équipe"),
    EQUIPIER("Équipier");

    private final String label;

    TypeFonction(String label) { this.label = label; }

    public String getLabel() { return label; }
}

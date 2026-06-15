package com.bryan.nexis.sapeurs.datamodel;

/**
 * Catégorie d'une fonction. Pilote :
 *  - l'ordre d'affichage de l'équipage au dispatch (ordre des constantes, Chef d'agrès en premier) ;
 *  - le {@code niveau} (CA 4 &gt; Conducteur 3 &gt; Chef d'équipe 2 &gt; Équipier 1) utilisé par
 *    l'affectation automatique (remplissage de bas en haut, réserve les CA aux postes CA).
 */
public enum TypeFonction {
    CHEF_AGRES("Chef d'agrès", 4),
    CONDUCTEUR("Conducteur", 3),
    CHEF_EQUIPE("Chef d'équipe", 2),
    EQUIPIER("Équipier", 1);

    private final String label;
    private final int niveau;

    TypeFonction(String label, int niveau) { this.label = label; this.niveau = niveau; }

    public String getLabel() { return label; }
    public int getNiveau()   { return niveau; }
}

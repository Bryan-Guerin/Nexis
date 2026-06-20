package com.bryan.nexis.sapeurs.datamodel;

/**
 * Flag d'intervention pouvant déclencher un lot de couverture (en plus de la nature).
 * Un {@link SpTemplateDepart} est rattaché soit à une nature, soit à un de ces flags.
 */
public enum DeclencheurFlag {
    INCENDIE,
    SR,
    VEHICULE_IMPLIQUE
}

package com.bryan.nexis.sapeurs.datamodel;

/** Famille de bilan rattachée à une intervention. Activables indépendamment. */
public enum FamilleBilan {
    /** Secours à personne — un bilan par victime. */
    SAP,
    /** Secours routier — un bilan par intervention. */
    SR,
    /** Incendie — un bilan par intervention. */
    INC
}

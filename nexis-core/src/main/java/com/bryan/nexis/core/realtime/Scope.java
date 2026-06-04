package com.bryan.nexis.core.realtime;

/** Cible d'un événement temps réel. */
public enum Scope {
    /** Tout le monde (rare). */
    ALL,
    /** Toutes les sessions d'une faction (via le rôle ROLE_xx). */
    FACTION,
    /** Des utilisateurs nominatifs (par username) — ex. l'équipage bipé. */
    USERS
}

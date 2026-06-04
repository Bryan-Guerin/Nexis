package com.bryan.nexis.sapeurs.backend.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;

/** Agrégats du tableau de bord SP. */
@Serdeable
public record SpDashboardDto(
        long effectifsActifs,
        int deGarde,
        int vehiculesTotal,
        int vehiculesDisponibles,
        int interventionsEnCoursTotal,
        long dureeMoyenneMinutes,
        List<EtatCount> flotte,
        List<GardeMembre> garde,
        List<EnginEngage> enginsEngages,
        List<Alerte> alertes,
        List<InterventionLigne> interventionsEnCours,
        List<JourActivite> activite7j
) {
    /** Répartition de la flotte par état maître. */
    @Serdeable
    public record EtatCount(String code, String label, String couleur, long count) {}

    /** Membre actuellement de garde (nominatif). */
    @Serdeable
    public record GardeMembre(String matricule, String username, String grade) {}

    /** Engin engagé sur une intervention, avec son statut RP. */
    @Serdeable
    public record EnginEngage(String libelle, String typeCode, String interventionCode, String statut, String couleur) {}

    /** Véhicule indisponible (maintenance / hors service) — alerte flotte. */
    @Serdeable
    public record Alerte(String libelle, String etat, String couleur) {}

    /** Ligne synthétique d'intervention en cours. */
    @Serdeable
    public record InterventionLigne(String code, String nature, String motif, Instant debut, int engins) {}

    /** Nombre d'interventions ouvertes sur un jour donné (tendance). */
    @Serdeable
    public record JourActivite(String jour, long count) {}
}

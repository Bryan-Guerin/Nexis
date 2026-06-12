package com.bryan.nexis.sapeurs.backend.vehicule;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Balaie périodiquement les affectations véhicule pour désaffecter automatiquement les
 * membres dont la garde est terminée (fin manuelle ou créneau de planning expiré).
 *
 * Répond au problème « pas d'event ni de CRON pour détecter l'expiration d'un créneau » :
 * un simple tick régulier recalcule {@code estDeGarde} et clôture ce qui doit l'être.
 */
@Singleton
public class SpAffectationSweeper {

    private static final Logger log = LoggerFactory.getLogger(SpAffectationSweeper.class);

    private final SpVehiculeAffectationService affectationService;

    public SpAffectationSweeper(SpVehiculeAffectationService affectationService) {
        this.affectationService = affectationService;
    }

    @Scheduled(fixedDelay = "5m", initialDelay = "1m")
    void sweep() {
        try {
            affectationService.cloturerExpirees();
        } catch (Exception e) {
            log.error("Échec du balayage de fin de garde", e);
        }
    }
}

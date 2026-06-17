package com.bryan.nexis.sapeurs.backend.effectif;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Évaluation périodique des badges.
 *
 * <p>Les conditions temporelles (ancienneté, jours dans le grade, heures de garde) et les
 * qualifications ajoutées ne déclenchent aucun événement : sans tick régulier, le badge ne
 * serait attribué qu'à la prochaine clôture d'intervention ou via le bouton « Évaluer » de l'admin.
 * Ce CRON complète la clôture d'intervention (qui reste le déclencheur immédiat des badges liés
 * aux interventions).</p>
 *
 * <p>{@code evalAll} est idempotent : un badge déjà obtenu n'est jamais re-attribué.</p>
 */
@Singleton
public class SpBadgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(SpBadgeScheduler.class);

    private final SpRpService rpService;

    public SpBadgeScheduler(SpRpService rpService) {
        this.rpService = rpService;
    }

    @Scheduled(fixedDelay = "1h", initialDelay = "2m")
    void evaluer() {
        try {
            int n = rpService.evalAll();
            if (n > 0) log.info("Évaluation périodique des badges : {} attribution(s).", n);
        } catch (Exception e) {
            log.error("Échec de l'évaluation périodique des badges", e);
        }
    }
}

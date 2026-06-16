-- Instant de début du trajet courant (dernier changement de statut) : anime le 🚒 + ETA.
ALTER TABLE sp_vehicule
    ADD COLUMN leg_depart TIMESTAMP;

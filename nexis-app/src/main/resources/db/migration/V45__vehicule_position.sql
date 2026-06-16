-- Dernière position connue d'un véhicule (point « sur place »), origine du trajet de retour.
ALTER TABLE sp_vehicule
    ADD COLUMN position_coordonnees VARCHAR(6);

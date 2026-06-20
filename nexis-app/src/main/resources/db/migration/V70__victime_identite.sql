-- Identité de la victime (saisie au popup d'ajout ; éditable tant que l'intervention est ouverte).
-- nom/prénom rebouclera avec le futur dossier médical ; sexe pilote la silhouette du schéma corporel.
ALTER TABLE sp_victime ADD COLUMN nom    VARCHAR(80);
ALTER TABLE sp_victime ADD COLUMN prenom VARCHAR(80);
ALTER TABLE sp_victime ADD COLUMN sexe   VARCHAR(1);   -- H | F (null = inconnu)

-- =====================================================================
-- V41 : coordonnées jeu (6 chiffres) d'une caserne, pour la carte.
-- =====================================================================

ALTER TABLE sp_centre ADD COLUMN coordonnees VARCHAR(6);

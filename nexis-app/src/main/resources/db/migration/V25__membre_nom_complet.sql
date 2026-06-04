-- =====================================================================
-- V25 : nom/prénom (libre, un seul champ) sur l'effectif, distinct du login
-- =====================================================================
ALTER TABLE sp_membre ADD COLUMN nom_complet VARCHAR(100);

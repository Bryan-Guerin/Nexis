-- =====================================================================
-- V22 : qualification de l'intervention (arbre décision) + renforts GN/VINCI
-- =====================================================================

-- Arbre décision (qualification de l'appel)
ALTER TABLE sp_intervention ADD COLUMN nb_victimes       INT;
ALTER TABLE sp_intervention ADD COLUMN incendie          BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sp_intervention ADD COLUMN vehicule_implique BOOLEAN NOT NULL DEFAULT FALSE;

-- Renforts (statut éditable par tous) : NON_PREVENU / PREVENU / SUR_PLACE
ALTER TABLE sp_intervention ADD COLUMN renfort_gn    VARCHAR(15) NOT NULL DEFAULT 'NON_PREVENU';
ALTER TABLE sp_intervention ADD COLUMN renfort_vinci VARCHAR(15) NOT NULL DEFAULT 'NON_PREVENU';

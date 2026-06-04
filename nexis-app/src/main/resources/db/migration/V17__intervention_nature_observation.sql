-- =====================================================================
-- V17 : intervention — nature obligatoire, "localisation" → "observation"
-- =====================================================================

-- Renommage du champ libre localisation → observation
ALTER TABLE sp_intervention RENAME COLUMN localisation TO observation;

-- Nature obligatoire : backfill des interventions sans nature vers "Divers", puis NOT NULL
UPDATE sp_intervention
SET nature_id = (SELECT id FROM sp_nature_intervention WHERE code = 'DIV')
WHERE nature_id IS NULL;
ALTER TABLE sp_intervention ALTER COLUMN nature_id SET NOT NULL;

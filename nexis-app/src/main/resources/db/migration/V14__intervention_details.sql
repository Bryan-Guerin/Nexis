-- =====================================================================
-- V14 : détails d'intervention + nature configurable + statut "défaut"
--       véhicule + référence sur le journal (lien intervention).
-- =====================================================================

-- Nature d'intervention (référentiel configurable)
CREATE TABLE sp_nature_intervention (
    id    UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code  VARCHAR(30)  NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    ordre INT          NOT NULL DEFAULT 0
);
INSERT INTO sp_nature_intervention (code, label, ordre) VALUES
    ('INC',  'Incendie',                     0),
    ('SAP',  'Secours à personne',           1),
    ('AVP',  'Accident de la voie publique', 2),
    ('DIV',  'Divers',                       3),
    ('NRBC', 'NRBC',                         4);

-- Champs supplémentaires sur l'intervention
ALTER TABLE sp_intervention ADD COLUMN numero       INT;
ALTER TABLE sp_intervention ADD COLUMN nature_id    UUID REFERENCES sp_nature_intervention(id);
ALTER TABLE sp_intervention ADD COLUMN requerant    VARCHAR(40);
ALTER TABLE sp_intervention ADD COLUMN telephone    VARCHAR(10);
ALTER TABLE sp_intervention ADD COLUMN localisation TEXT;
ALTER TABLE sp_intervention ADD COLUMN commune      VARCHAR(40);
ALTER TABLE sp_intervention ADD COLUMN coordonnees  VARCHAR(6);

-- Numérotation des interventions existantes
WITH r AS (SELECT id, (ROW_NUMBER() OVER (ORDER BY debut))::INT AS n FROM sp_intervention)
UPDATE sp_intervention SET numero = r.n FROM r WHERE sp_intervention.id = r.id;

-- Statut "par défaut" (ex. Engagé, appliqué aux engins lors d'un déclenchement)
ALTER TABLE sp_vehicule_etat ADD COLUMN par_defaut BOOLEAN NOT NULL DEFAULT FALSE;

-- Référence générique sur le journal (relie un événement à une intervention, etc.)
ALTER TABLE journal_evenement ADD COLUMN reference VARCHAR(40);
CREATE INDEX idx_journal_reference ON journal_evenement(reference);

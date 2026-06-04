-- =====================================================================
-- V8 : ordre persisté des éléments de configuration SP (comme une enum)
--      + nouvelle entité "casier" gérée en configuration.
-- =====================================================================

-- Colonne d'ordre sur les référentiels existants
ALTER TABLE sp_grade         ADD COLUMN ordre INT NOT NULL DEFAULT 0;
ALTER TABLE sp_fonction      ADD COLUMN ordre INT NOT NULL DEFAULT 0;
ALTER TABLE sp_vehicule_etat ADD COLUMN ordre INT NOT NULL DEFAULT 0;

-- Initialisation de l'ordre sur les données existantes (par libellé)
WITH r AS (SELECT id, (ROW_NUMBER() OVER (ORDER BY label) - 1) AS p FROM sp_grade)
UPDATE sp_grade SET ordre = r.p FROM r WHERE sp_grade.id = r.id;

WITH r AS (SELECT id, (ROW_NUMBER() OVER (ORDER BY label) - 1) AS p FROM sp_fonction)
UPDATE sp_fonction SET ordre = r.p FROM r WHERE sp_fonction.id = r.id;

WITH r AS (SELECT id, (ROW_NUMBER() OVER (ORDER BY label) - 1) AS p FROM sp_vehicule_etat)
UPDATE sp_vehicule_etat SET ordre = r.p FROM r WHERE sp_vehicule_etat.id = r.id;

-- Casiers : liste configurable (numéro + ordre)
CREATE TABLE sp_casier (
    id     UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    numero INT  NOT NULL UNIQUE,
    ordre  INT  NOT NULL DEFAULT 0
);

-- Amorçage avec les casiers 0 → 30 (convention actuelle des effectifs)
INSERT INTO sp_casier (numero, ordre)
SELECT g, g FROM generate_series(0, 30) AS g;

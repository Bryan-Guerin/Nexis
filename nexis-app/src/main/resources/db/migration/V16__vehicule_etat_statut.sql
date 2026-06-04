-- =====================================================================
-- V16 : modèle véhicule à deux axes
--   - ÉTAT   : référentiel système maître (garant du code)
--              Disponible / Maintenance / Inventaire / Indisponible
--   - STATUT : référentiel RP ordonné, configurable, chaque statut lié
--              à un état. Transition avant uniquement (cf. service).
--   - Suppression de la "condition" (équipé/armé/cassé), remplacée par l'état.
-- =====================================================================

-- 1) L'actuel référentiel sp_vehicule_etat devient le STATUT (RP, ordonné)
ALTER TABLE sp_vehicule_etat RENAME TO sp_vehicule_statut;
ALTER TABLE sp_vehicule RENAME COLUMN etat_id TO statut_id;

-- 2) Nouveau référentiel ÉTAT (maître, système)
CREATE TABLE sp_vehicule_etat (
    id      UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code    VARCHAR(30)  NOT NULL UNIQUE,
    label   VARCHAR(100) NOT NULL,
    couleur VARCHAR(7),
    ordre   INT          NOT NULL DEFAULT 0
);
INSERT INTO sp_vehicule_etat (code, label, couleur, ordre) VALUES
    ('DISPONIBLE',   'Disponible',   '#4caf82', 0),
    ('MAINTENANCE',  'Maintenance',  '#e0a23c', 1),
    ('INVENTAIRE',   'Inventaire',   '#4f6ef7', 2),
    ('INDISPONIBLE', 'Indisponible', '#e05c5c', 3);

-- 3) Chaque statut bascule le véhicule dans un état (lien configurable)
ALTER TABLE sp_vehicule_statut ADD COLUMN etat_id UUID REFERENCES sp_vehicule_etat(id);

-- 4) Colonne maître (état) sur le véhicule
ALTER TABLE sp_vehicule ADD COLUMN etat_id UUID REFERENCES sp_vehicule_etat(id);
UPDATE sp_vehicule SET etat_id = (SELECT id FROM sp_vehicule_etat WHERE code = 'DISPONIBLE');

-- 5) Réinitialiser la liste des statuts RP (nouvelle enum ordonnée).
--    Statut tampon → repointage des véhicules → purge → insertion → repointage final.
INSERT INTO sp_vehicule_statut (id, code, label, couleur, ordre, par_defaut, etat_id)
VALUES (gen_random_uuid(), '__TMP__', 'tmp', '#000000', 999, FALSE,
        (SELECT id FROM sp_vehicule_etat WHERE code = 'DISPONIBLE'));
UPDATE sp_vehicule SET statut_id = (SELECT id FROM sp_vehicule_statut WHERE code = '__TMP__');
DELETE FROM sp_vehicule_statut WHERE code <> '__TMP__';

INSERT INTO sp_vehicule_statut (code, label, couleur, ordre, par_defaut, etat_id)
SELECT s.code, s.label, s.couleur, s.ordre, (s.ordre = 0), e.id
FROM (VALUES
    ('DECLENCHE',     'Déclenché',        '#e05c5c', 0, 'INDISPONIBLE'),
    ('EN_ROUTE',      'En route',         '#e0723c', 1, 'INDISPONIBLE'),
    ('SUR_LES_LIEUX', 'Sur les lieux',    '#e0a23c', 2, 'INDISPONIBLE'),
    ('TRANSPORT_CH',  'Transport CH',     '#d6c33a', 3, 'INDISPONIBLE'),
    ('ARRIVE_CH',     'Arrivé CH',        '#9ec23a', 4, 'INDISPONIBLE'),
    ('DISPO_RADIO',   'Disponible radio', '#4caf82', 5, 'DISPONIBLE'),
    ('DISPONIBLE',    'Disponible',       '#3a9d72', 6, 'DISPONIBLE')
) AS s(code, label, couleur, ordre, etat_code)
JOIN sp_vehicule_etat e ON e.code = s.etat_code;

UPDATE sp_vehicule SET statut_id = (SELECT id FROM sp_vehicule_statut WHERE code = 'DISPONIBLE');
DELETE FROM sp_vehicule_statut WHERE code = '__TMP__';

-- 6) Contraintes NOT NULL
ALTER TABLE sp_vehicule_statut ALTER COLUMN etat_id SET NOT NULL;
ALTER TABLE sp_vehicule ALTER COLUMN etat_id SET NOT NULL;

-- 7) Suppression de la condition (équipé/armé/cassé), remplacée par l'état maître
ALTER TABLE sp_vehicule DROP COLUMN condition_id;
DROP TABLE IF EXISTS sp_vehicule_condition;

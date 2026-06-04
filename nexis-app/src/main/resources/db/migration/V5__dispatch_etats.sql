-- =====================================================================
-- V5 : états de véhicule configurables (GN + SP)
-- =====================================================================

CREATE TABLE gn_vehicule_etat (
    id      UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code    VARCHAR(30)  NOT NULL UNIQUE,
    label   VARCHAR(100) NOT NULL,
    couleur VARCHAR(7)
);

INSERT INTO gn_vehicule_etat (code, label, couleur) VALUES
    ('DISPONIBLE',     'Disponible',   '#4caf82'),
    ('EN_SERVICE',     'En service',   '#4f6ef7'),
    ('HORS_SERVICE',   'Hors service', '#e05c5c'),
    ('EN_MAINTENANCE', 'Maintenance',  '#e8a23a');

CREATE TABLE sp_vehicule_etat (
    id      UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code    VARCHAR(30)  NOT NULL UNIQUE,
    label   VARCHAR(100) NOT NULL,
    couleur VARCHAR(7)
);

INSERT INTO sp_vehicule_etat (code, label, couleur) VALUES
    ('DISPONIBLE',     'Disponible',   '#4caf82'),
    ('EN_SERVICE',     'En service',   '#4f6ef7'),
    ('HORS_SERVICE',   'Hors service', '#e05c5c'),
    ('EN_MAINTENANCE', 'Maintenance',  '#e8a23a');

-- Migrer gn_vehicule.etat (VARCHAR enum → FK)
ALTER TABLE gn_vehicule ADD COLUMN etat_id UUID REFERENCES gn_vehicule_etat(id);
UPDATE gn_vehicule SET etat_id = (SELECT id FROM gn_vehicule_etat WHERE code = etat);
ALTER TABLE gn_vehicule DROP COLUMN etat;

-- Migrer sp_vehicule.etat (VARCHAR enum → FK)
ALTER TABLE sp_vehicule ADD COLUMN etat_id UUID REFERENCES sp_vehicule_etat(id);
UPDATE sp_vehicule SET etat_id = (SELECT id FROM sp_vehicule_etat WHERE code = etat);
ALTER TABLE sp_vehicule DROP COLUMN etat;

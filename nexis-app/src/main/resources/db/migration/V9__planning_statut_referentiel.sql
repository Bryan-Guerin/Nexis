-- =====================================================================
-- V9 : le statut de planning devient un référentiel configurable par
--      faction (code, label, couleur, ordre, catégorie). La catégorie
--      (GARDE / ASTREINTE / AUTRE) porte le sens transverse "en service".
--      Remplace l'enum StatutDisponibilite stocké en VARCHAR.
-- =====================================================================

-- ── Sapeurs-Pompiers ────────────────────────────────────────────────────────
CREATE TABLE sp_planning_statut (
    id        UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code      VARCHAR(30)  NOT NULL UNIQUE,
    label     VARCHAR(100) NOT NULL,
    couleur   VARCHAR(7),
    ordre     INT          NOT NULL DEFAULT 0,
    categorie VARCHAR(20)  NOT NULL
);

INSERT INTO sp_planning_statut (code, label, couleur, ordre, categorie) VALUES
    ('GARDE',     'Garde',     '#4f6ef7', 0, 'GARDE'),
    ('ASTREINTE', 'Astreinte', '#e8a23a', 1, 'ASTREINTE'),
    ('FORMATION', 'Formation', '#4caf82', 2, 'AUTRE'),
    ('ABSENT',    'Absent',    '#888888', 3, 'AUTRE');

ALTER TABLE sp_planning ADD COLUMN statut_id UUID REFERENCES sp_planning_statut(id);
UPDATE sp_planning SET statut_id = (SELECT id FROM sp_planning_statut s WHERE s.code = sp_planning.statut);
ALTER TABLE sp_planning ALTER COLUMN statut_id SET NOT NULL;
ALTER TABLE sp_planning DROP COLUMN statut;

-- ── Gendarmerie ─────────────────────────────────────────────────────────────
CREATE TABLE gn_planning_statut (
    id        UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code      VARCHAR(30)  NOT NULL UNIQUE,
    label     VARCHAR(100) NOT NULL,
    couleur   VARCHAR(7),
    ordre     INT          NOT NULL DEFAULT 0,
    categorie VARCHAR(20)  NOT NULL
);

INSERT INTO gn_planning_statut (code, label, couleur, ordre, categorie) VALUES
    ('GARDE',     'Garde',     '#4f6ef7', 0, 'GARDE'),
    ('ASTREINTE', 'Astreinte', '#e8a23a', 1, 'ASTREINTE'),
    ('FORMATION', 'Formation', '#4caf82', 2, 'AUTRE'),
    ('ABSENT',    'Absent',    '#888888', 3, 'AUTRE');

ALTER TABLE gn_planning ADD COLUMN statut_id UUID REFERENCES gn_planning_statut(id);
UPDATE gn_planning SET statut_id = (SELECT id FROM gn_planning_statut s WHERE s.code = gn_planning.statut);
ALTER TABLE gn_planning ALTER COLUMN statut_id SET NOT NULL;
ALTER TABLE gn_planning DROP COLUMN statut;

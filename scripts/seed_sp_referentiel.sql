-- =====================================================================
-- Seed du référentiel Sapeurs-Pompiers (grades, fonctions, objets d'inventaire).
-- Idempotent : ON CONFLICT (code) → met à jour label + ordre, ne duplique pas.
--
-- Lancement (base hôte) :
--   sudo -u postgres psql -d nexis -f scripts/seed_sp_referentiel.sql
-- ou :
--   psql -h localhost -U nexis -d nexis -f scripts/seed_sp_referentiel.sql
-- =====================================================================

-- ── Grades SP ────────────────────────────────────────────────────────
INSERT INTO sp_grade (code, label, ordre) VALUES
    ('1Cl', 'Sapeur de première classe', 0),
    ('2CL', 'Sapeur de seconde classe',  1),
    ('CPL', 'Caporal',                   2),
    ('CCH', 'Caporal-chef',              3),
    ('SGT', 'Sergent',                   4),
    ('SCH', 'Sergent-chef',              5),
    ('ADJ', 'Adjudant',                  6),
    ('ADC', 'Adjudant-chef',             7)
ON CONFLICT (code) DO UPDATE SET label = EXCLUDED.label, ordre = EXCLUDED.ordre;

-- ── Fonctions SP ─────────────────────────────────────────────────────
INSERT INTO sp_fonction (code, label, ordre) VALUES
    ('COD 0',  'COD 0',                0),
    ('EQ SAP', 'Equipier SAP',         1),
    ('EQ INC', 'Equipier incendie',    2),
    ('EQ SR',  'Equipier SR',          3),
    ('CE INC', 'Chef Equipe Incendie', 4),
    ('CA VSAV','CA VSAV',              5),
    ('CATE',   'CA tout engin',        6),
    ('COD 1',  'COD 1',                7),
    ('COD 2',  'COD 2',                8),
    ('COD 4',  'COD 4',                9),
    ('COD 6',  'COD 6',               10)
ON CONFLICT (code) DO UPDATE SET label = EXCLUDED.label, ordre = EXCLUDED.ordre;

-- ── Objets d'inventaire ──────────────────────────────────────────────
-- NB : les 5 premiers (BRANCARD…EXTINCTEUR) sont déjà créés par la migration V15 ;
--      l'ON CONFLICT se contente de réaligner label/ordre.
INSERT INTO sp_objet_inventaire (code, label, ordre) VALUES
    ('BRANCARD',        'Brancard',                  0),
    ('DEFIB',           'Défibrillateur',            1),
    ('O2',              'Bouteille O2',              2),
    ('BALISAGE',        'Lot de balisage',           3),
    ('EXTINCTEUR',      'Extincteur',                4),
    ('BANDAGE',         'Bandages',                  5),
    ('CONE_LUBECK',     'Cônes de Lübeck',           6),
    ('LDV',             'Lance incendie',            7),
    ('TUYAU100M',       'Tuyau d''alimentation 100m', 8),
    ('TUYAU70M',        'Tuyau division 70m',        9),
    ('TUYAU_40M',       'Tuyau d''attaque 40m',      10),
    ('ARI',             'ARI + masque',              11),
    ('GILET_SP_ORANGE', 'Gilet SP orange',           12),
    ('GILET_SP_CA',     'Gilet SP CA',               13),
    ('GILET_SP_CDG',    'Gilet SP CDG',              14),
    ('GILET_SP_MED',    'Gilet MED',                 15),
    ('GILET_INF',       'Gilet INF',                 16),
    ('GANTS_SUAP',      'Gants SUAP',                17)
ON CONFLICT (code) DO UPDATE SET label = EXCLUDED.label, ordre = EXCLUDED.ordre;

-- =====================================================================
-- V12 : enrichissement véhicule SP
--   - état/condition (référentiel configurable : équipé/armé/cassé)
--   - centre (caserne) : référentiel configurable
--   - capacité eau, (commentaire = colonne notes déjà existante)
--   - inventaire porté par le type + vérifications (checklist + historique)
-- =====================================================================

-- ── État / condition matériel (référentiel) ─────────────────────────────────
CREATE TABLE sp_vehicule_condition (
    id      UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code    VARCHAR(30)  NOT NULL UNIQUE,
    label   VARCHAR(100) NOT NULL,
    couleur VARCHAR(7),
    ordre   INT          NOT NULL DEFAULT 0
);
INSERT INTO sp_vehicule_condition (code, label, couleur, ordre) VALUES
    ('ARME',   'Armé',   '#4caf82', 0),
    ('EQUIPE', 'Équipé', '#4f6ef7', 1),
    ('CASSE',  'Cassé',  '#e05c5c', 2);

-- ── Centre (caserne) ─────────────────────────────────────────────────────────
CREATE TABLE sp_centre (
    id    UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code  VARCHAR(30)  NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    ordre INT          NOT NULL DEFAULT 0
);

-- ── Colonnes véhicule ────────────────────────────────────────────────────────
ALTER TABLE sp_vehicule ADD COLUMN condition_id UUID REFERENCES sp_vehicule_condition(id);
ALTER TABLE sp_vehicule ADD COLUMN centre_id    UUID REFERENCES sp_centre(id);
ALTER TABLE sp_vehicule ADD COLUMN capacite_eau INT;

-- ── Inventaire (modèle porté par le type) ────────────────────────────────────
CREATE TABLE sp_inventaire_item (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    vehicule_type_id UUID         NOT NULL REFERENCES sp_vehicule_type(id) ON DELETE CASCADE,
    libelle          VARCHAR(150) NOT NULL,
    ordre            INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_sp_inventaire_type ON sp_inventaire_item(vehicule_type_id);

-- ── Vérifications (sessions de check + historique) ───────────────────────────
CREATE TABLE sp_verification (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    vehicule_id UUID        NOT NULL REFERENCES sp_vehicule(id) ON DELETE CASCADE,
    cree_le     TIMESTAMPTZ NOT NULL DEFAULT now(),
    par         VARCHAR(50)
);
CREATE INDEX idx_sp_verification_vehicule ON sp_verification(vehicule_id, cree_le DESC);

CREATE TABLE sp_verification_ligne (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    verification_id UUID         NOT NULL REFERENCES sp_verification(id) ON DELETE CASCADE,
    libelle         VARCHAR(150) NOT NULL,
    present         BOOLEAN      NOT NULL DEFAULT TRUE,
    commentaire     TEXT
);

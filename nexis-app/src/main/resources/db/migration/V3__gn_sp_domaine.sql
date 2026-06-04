-- =====================================================================
-- Gendarmerie Nationale
-- =====================================================================

CREATE TABLE gn_vehicule_type (
    id    UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code  VARCHAR(30)  NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL
);

CREATE TABLE gn_vehicule (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    type_id         UUID         NOT NULL REFERENCES gn_vehicule_type(id) ON DELETE RESTRICT,
    immatriculation VARCHAR(20)  UNIQUE,
    libelle         VARCHAR(100) NOT NULL,
    etat            VARCHAR(20)  NOT NULL DEFAULT 'DISPONIBLE',
    notes           TEXT
);

CREATE TABLE gn_membre (
    id        UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id   UUID        NOT NULL UNIQUE REFERENCES ref_user(id) ON DELETE RESTRICT,
    grade     VARCHAR(50) NOT NULL,
    matricule VARCHAR(20) NOT NULL UNIQUE,
    actif     BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE gn_planning (
    id        UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    membre_id UUID        NOT NULL REFERENCES gn_membre(id) ON DELETE CASCADE,
    debut     TIMESTAMPTZ NOT NULL,
    fin       TIMESTAMPTZ NOT NULL,
    statut    VARCHAR(20) NOT NULL,
    notes     TEXT
);

CREATE TABLE gn_vehicule_affectation (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    vehicule_id UUID        NOT NULL REFERENCES gn_vehicule(id) ON DELETE RESTRICT,
    membre_id   UUID        NOT NULL REFERENCES gn_membre(id) ON DELETE RESTRICT,
    debut       TIMESTAMPTZ NOT NULL,
    fin         TIMESTAMPTZ
);

CREATE INDEX idx_gn_planning_membre      ON gn_planning(membre_id);
CREATE INDEX idx_gn_planning_dates       ON gn_planning(debut, fin);
CREATE INDEX idx_gn_affectation_vehicule ON gn_vehicule_affectation(vehicule_id);
CREATE INDEX idx_gn_affectation_dates    ON gn_vehicule_affectation(debut, fin);

-- =====================================================================
-- Sapeurs-Pompiers
-- =====================================================================

CREATE TABLE sp_vehicule_type (
    id    UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code  VARCHAR(30)  NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL
);

CREATE TABLE sp_vehicule_type_poste (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    vehicule_type_id UUID        NOT NULL REFERENCES sp_vehicule_type(id) ON DELETE CASCADE,
    code_poste       VARCHAR(30) NOT NULL,
    label_poste      VARCHAR(100) NOT NULL,
    nb_places        SMALLINT    NOT NULL DEFAULT 1,
    UNIQUE (vehicule_type_id, code_poste)
);

CREATE TABLE sp_vehicule (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    type_id         UUID         NOT NULL REFERENCES sp_vehicule_type(id) ON DELETE RESTRICT,
    immatriculation VARCHAR(20)  UNIQUE,
    libelle         VARCHAR(100) NOT NULL,
    etat            VARCHAR(20)  NOT NULL DEFAULT 'DISPONIBLE',
    notes           TEXT
);

CREATE TABLE sp_membre (
    id        UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id   UUID        NOT NULL UNIQUE REFERENCES ref_user(id) ON DELETE RESTRICT,
    grade     VARCHAR(50) NOT NULL,
    matricule VARCHAR(20) NOT NULL UNIQUE,
    actif     BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE sp_qualification (
    membre_id UUID NOT NULL REFERENCES sp_membre(id) ON DELETE CASCADE,
    poste_id  UUID NOT NULL REFERENCES sp_vehicule_type_poste(id) ON DELETE CASCADE,
    PRIMARY KEY (membre_id, poste_id)
);

CREATE TABLE sp_planning (
    id        UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    membre_id UUID        NOT NULL REFERENCES sp_membre(id) ON DELETE CASCADE,
    debut     TIMESTAMPTZ NOT NULL,
    fin       TIMESTAMPTZ NOT NULL,
    statut    VARCHAR(20) NOT NULL,
    notes     TEXT
);

CREATE TABLE sp_vehicule_affectation (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    vehicule_id UUID        NOT NULL REFERENCES sp_vehicule(id) ON DELETE RESTRICT,
    membre_id   UUID        NOT NULL REFERENCES sp_membre(id) ON DELETE RESTRICT,
    poste_id    UUID        NOT NULL REFERENCES sp_vehicule_type_poste(id) ON DELETE RESTRICT,
    debut       TIMESTAMPTZ NOT NULL,
    fin         TIMESTAMPTZ
);

CREATE INDEX idx_sp_planning_membre      ON sp_planning(membre_id);
CREATE INDEX idx_sp_planning_dates       ON sp_planning(debut, fin);
CREATE INDEX idx_sp_affectation_vehicule ON sp_vehicule_affectation(vehicule_id);
CREATE INDEX idx_sp_affectation_dates    ON sp_vehicule_affectation(debut, fin);

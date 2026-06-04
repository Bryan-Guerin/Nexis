-- =====================================================================
-- V20 : dates effectif, métadonnées de qualification, notation (commune GN+SP)
-- =====================================================================

-- Effectif : date d'intégration (= création) et date de dernière promotion (changement de grade)
ALTER TABLE sp_membre ADD COLUMN date_integration TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE sp_membre ADD COLUMN date_derniere_promotion TIMESTAMPTZ;
UPDATE sp_membre SET date_derniere_promotion = date_integration WHERE date_derniere_promotion IS NULL;

-- Qualification : la table de jonction devient une entité (date de délivrance + délivré par)
ALTER TABLE sp_membre_qualification ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE sp_membre_qualification ADD COLUMN date_delivrance TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE sp_membre_qualification ADD COLUMN delivre_par VARCHAR(50);
ALTER TABLE sp_membre_qualification DROP CONSTRAINT sp_membre_qualification_pkey;
ALTER TABLE sp_membre_qualification ADD PRIMARY KEY (id);
ALTER TABLE sp_membre_qualification ADD CONSTRAINT uq_membre_fonction UNIQUE (membre_id, fonction_id);

-- Notation mensuelle (commune à toutes les factions : faction + membre_id, sans FK inter-module)
CREATE TABLE notation (
    id                       UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    faction                  VARCHAR(10) NOT NULL,
    membre_id                UUID        NOT NULL,
    mois                     VARCHAR(7)  NOT NULL,   -- AAAA-MM
    comportement_discipline  INT         NOT NULL,
    competences_techniques   INT         NOT NULL,
    aptitude_physique        INT         NOT NULL,
    initiative_autonomie     INT         NOT NULL,
    esprit_equipe            INT         NOT NULL,
    respect_securite         INT         NOT NULL,
    observations             TEXT,
    objectifs                TEXT,
    evaluateur               VARCHAR(50),
    cree_le                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notation_membre ON notation(faction, membre_id, mois DESC);

-- =====================================================================
-- V36 : suivi de trésorerie SP (RH / admin uniquement). Un compte unique
--       (solde initial + libellé), des catégories configurables, et un
--       journal de mouvements gains/dépenses historisé.
-- =====================================================================

CREATE TABLE sp_finance_compte (
    id            UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    libelle       VARCHAR(100)  NOT NULL,
    solde_initial NUMERIC(12,2) NOT NULL DEFAULT 0
);
INSERT INTO sp_finance_compte (libelle, solde_initial) VALUES ('Compte SDIS', 0);

CREATE TABLE sp_finance_categorie (
    id      UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    libelle VARCHAR(60) NOT NULL UNIQUE
);
INSERT INTO sp_finance_categorie (libelle) VALUES
    ('Carburant'), ('Matériel'), ('Subvention'), ('Divers');

CREATE TABLE sp_finance_mouvement (
    id            UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    type          VARCHAR(10)   NOT NULL,
    montant       NUMERIC(12,2) NOT NULL,
    libelle       VARCHAR(150)  NOT NULL,
    date_mouvement DATE         NOT NULL,
    categorie_id  UUID          REFERENCES sp_finance_categorie(id) ON DELETE SET NULL,
    cree_par      VARCHAR(50),
    cree_le       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_finance_mvt_date ON sp_finance_mouvement (date_mouvement DESC);

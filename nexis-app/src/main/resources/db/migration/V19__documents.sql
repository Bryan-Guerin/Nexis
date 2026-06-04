-- =====================================================================
-- V19 : documents SP (formations, notes de service…) — PDF en base
-- =====================================================================

CREATE TABLE sp_document_categorie (
    id    UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    nom   VARCHAR(100) NOT NULL UNIQUE,
    ordre INT          NOT NULL DEFAULT 0
);

CREATE TABLE sp_document (
    id           UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    categorie_id UUID         NOT NULL REFERENCES sp_document_categorie(id) ON DELETE CASCADE,
    nom          VARCHAR(150) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    taille       BIGINT       NOT NULL,
    contenu      BYTEA        NOT NULL,
    cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par     VARCHAR(50)
);
CREATE INDEX idx_sp_document_categorie ON sp_document(categorie_id, cree_le DESC);

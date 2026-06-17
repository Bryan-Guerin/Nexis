-- Fonctions organigramme : rôles dans la caserne (Chef de centre, RH, Chef de garde…),
-- structurées en arbre (parent_id auto-référencé). Un membre peut cumuler plusieurs fonctions.
CREATE TABLE sp_fonction_orga (
    id        UUID PRIMARY KEY,
    code      VARCHAR(30)  NOT NULL UNIQUE,
    label     VARCHAR(100) NOT NULL,
    parent_id UUID         REFERENCES sp_fonction_orga(id) ON DELETE SET NULL,
    ordre     INT          NOT NULL DEFAULT 0,
    icone     VARCHAR(8)
);
CREATE INDEX idx_sp_fonction_orga_parent ON sp_fonction_orga(parent_id);

-- Table d'association membre ↔ fonction (cumul possible).
CREATE TABLE sp_membre_fonction_orga (
    membre_id        UUID NOT NULL REFERENCES sp_membre(id)        ON DELETE CASCADE,
    fonction_orga_id UUID NOT NULL REFERENCES sp_fonction_orga(id) ON DELETE CASCADE,
    PRIMARY KEY (membre_id, fonction_orga_id)
);
CREATE INDEX idx_sp_mfo_fonction ON sp_membre_fonction_orga(fonction_orga_id);

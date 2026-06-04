-- =====================================================================
-- V24 : relances (rappels manuels RH/admin sur un effectif, ex. recyclages)
-- =====================================================================

CREATE TABLE sp_relance (
    id        UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    membre_id UUID        NOT NULL REFERENCES sp_membre(id) ON DELETE CASCADE,
    texte     TEXT        NOT NULL,
    echeance  DATE,
    statut    VARCHAR(10) NOT NULL DEFAULT 'OUVERT',   -- OUVERT / FAIT
    cree_par  VARCHAR(50),
    cree_le   TIMESTAMPTZ NOT NULL DEFAULT now(),
    fait_par  VARCHAR(50),
    fait_le   TIMESTAMPTZ
);
CREATE INDEX idx_sp_relance_membre ON sp_relance(membre_id);
CREATE INDEX idx_sp_relance_statut ON sp_relance(statut, echeance);

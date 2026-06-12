-- Sanctions disciplinaires sur un effectif SP (gérées par RH / admin).
CREATE TABLE sp_sanction (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    membre_id     UUID        NOT NULL REFERENCES sp_membre(id) ON DELETE CASCADE,
    type          VARCHAR(40),
    motif         TEXT        NOT NULL,
    date_sanction DATE        NOT NULL,
    cree_par      VARCHAR(50),
    cree_le       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

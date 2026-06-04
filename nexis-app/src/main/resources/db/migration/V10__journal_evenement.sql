-- =====================================================================
-- V10 : journal des événements de domaine (socle main courante + audit).
--       Alimenté par le bus temps réel (publication after-commit).
-- =====================================================================

CREATE TABLE journal_evenement (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    type            VARCHAR(40)  NOT NULL,
    faction         VARCHAR(10),
    acteur_username VARCHAR(50),
    message         TEXT         NOT NULL,
    cree_le         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_journal_cree_le ON journal_evenement(cree_le DESC);
CREATE INDEX idx_journal_faction ON journal_evenement(faction);

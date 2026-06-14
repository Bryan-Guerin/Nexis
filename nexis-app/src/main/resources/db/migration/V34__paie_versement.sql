-- =====================================================================
-- V34 : trace des versements de paie SP. Une ligne par membre et par
--       semaine (lundi). La présence de lignes marque la semaine réglée.
--       Sert d'historique (qui a réglé, quand, combien) et de support à
--       la notification « vous avez été payé ».
-- =====================================================================

CREATE TABLE sp_paie_versement (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    membre_id     UUID         NOT NULL REFERENCES sp_membre(id),
    semaine_lundi DATE         NOT NULL,
    montant       NUMERIC(10,2) NOT NULL,
    regle_par     VARCHAR(50),
    regle_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (membre_id, semaine_lundi)
);

CREATE INDEX idx_paie_versement_semaine ON sp_paie_versement (semaine_lundi);
CREATE INDEX idx_paie_versement_membre  ON sp_paie_versement (membre_id);

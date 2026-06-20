-- Bilans d'intervention (SAP / SR / INC), distincts du CRI. Contenu = document JSON (jsonb) dont
-- la forme est typée côté Java (records par famille) → extensible sans migration. SAP : un bilan
-- par victime ; SR / INC : un bilan par intervention. Victimes : 1..n par intervention (unité SAP
-- + ancre du futur dossier médical).

CREATE TABLE sp_victime (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intervention_id UUID NOT NULL REFERENCES sp_intervention(id) ON DELETE CASCADE,
    numero          INT  NOT NULL,
    libelle         VARCHAR(120),
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (intervention_id, numero)
);
CREATE INDEX idx_victime_intervention ON sp_victime (intervention_id);

CREATE TABLE sp_bilan (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intervention_id UUID NOT NULL REFERENCES sp_intervention(id) ON DELETE CASCADE,
    famille         VARCHAR(10) NOT NULL,                  -- SAP | SR | INC
    victime_id      UUID REFERENCES sp_victime(id) ON DELETE CASCADE,
    contenu         JSONB NOT NULL DEFAULT '{}'::jsonb,
    auteur          VARCHAR(50),
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now(),
    maj_le          TIMESTAMPTZ,
    -- SAP ⇔ victime renseignée ; SR / INC ⇔ victime nulle.
    CONSTRAINT ck_bilan_sap_victime CHECK ((famille = 'SAP') = (victime_id IS NOT NULL))
);
CREATE INDEX idx_bilan_intervention ON sp_bilan (intervention_id);
-- Un bilan SAP par victime ; un bilan SR / INC par intervention.
CREATE UNIQUE INDEX uq_bilan_victime ON sp_bilan (victime_id) WHERE victime_id IS NOT NULL;
CREATE UNIQUE INDEX uq_bilan_inter_famille ON sp_bilan (intervention_id, famille) WHERE victime_id IS NULL;

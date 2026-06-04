-- =====================================================================
-- V11 : interventions SP. Une intervention représente l'action d'un ou
--       plusieurs engins ; ouverte à la création, clôturée ensuite.
--       On peut ajouter des engins en renfort sur une intervention en cours.
-- =====================================================================

CREATE TABLE sp_intervention (
    id        UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    motif     VARCHAR(200) NOT NULL,
    debut     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fin       TIMESTAMPTZ,
    cree_par  VARCHAR(50)
);

CREATE TABLE sp_intervention_vehicule (
    intervention_id UUID NOT NULL REFERENCES sp_intervention(id) ON DELETE CASCADE,
    vehicule_id     UUID NOT NULL REFERENCES sp_vehicule(id)     ON DELETE RESTRICT,
    PRIMARY KEY (intervention_id, vehicule_id)
);

CREATE INDEX idx_sp_intervention_fin ON sp_intervention(fin);

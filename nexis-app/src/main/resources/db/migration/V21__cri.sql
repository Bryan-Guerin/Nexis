-- =====================================================================
-- V21 : compte rendu d'intervention (CRI) — 1 par véhicule engagé
--   Cycle : BROUILLON (équipage) → SOUMIS (équipage) → VALIDE (admin SP)
--   Contenu volontairement générique pour l'instant (texte libre).
-- =====================================================================

CREATE TABLE sp_cri (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    intervention_id UUID         NOT NULL REFERENCES sp_intervention(id) ON DELETE CASCADE,
    vehicule_id     UUID         NOT NULL REFERENCES sp_vehicule(id),
    contenu         TEXT,
    statut          VARCHAR(15)  NOT NULL DEFAULT 'BROUILLON',
    soumis_par      VARCHAR(50),
    soumis_le       TIMESTAMPTZ,
    valide_par      VARCHAR(50),
    valide_le       TIMESTAMPTZ,
    cree_le         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (intervention_id, vehicule_id)
);
CREATE INDEX idx_sp_cri_intervention ON sp_cri(intervention_id);

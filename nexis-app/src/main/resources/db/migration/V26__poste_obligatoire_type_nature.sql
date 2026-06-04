-- =====================================================================
-- V26 : poste obligatoire (→ véhicule "armé") + tags type de véhicule ↔ nature
-- =====================================================================

-- Poste obligatoire : nécessaire pour qu'un véhicule soit "armé"
ALTER TABLE sp_vehicule_type_poste ADD COLUMN obligatoire BOOLEAN NOT NULL DEFAULT FALSE;

-- Tags type de véhicule ↔ nature d'intervention (M:N) — pour la proposition d'engins
CREATE TABLE sp_vehicule_type_nature (
    type_id   UUID NOT NULL REFERENCES sp_vehicule_type(id)       ON DELETE CASCADE,
    nature_id UUID NOT NULL REFERENCES sp_nature_intervention(id) ON DELETE CASCADE,
    PRIMARY KEY (type_id, nature_id)
);

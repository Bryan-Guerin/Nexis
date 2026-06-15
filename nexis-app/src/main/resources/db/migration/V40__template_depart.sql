-- =====================================================================
-- V40 : lots de départ — par nature d'intervention, des types de véhicule
--       à engager (avec quantité). Support du bouton « Engager le lot ».
-- =====================================================================

CREATE TABLE sp_template_depart (
    id              UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    nature_id       UUID NOT NULL REFERENCES sp_nature_intervention(id) ON DELETE CASCADE,
    vehicule_type_id UUID NOT NULL REFERENCES sp_vehicule_type(id) ON DELETE CASCADE,
    quantite        INT  NOT NULL DEFAULT 1,
    ordre           INT  NOT NULL DEFAULT 0,
    UNIQUE (nature_id, vehicule_type_id)
);
CREATE INDEX idx_template_depart_nature ON sp_template_depart (nature_id);

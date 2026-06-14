-- =====================================================================
-- V38 : un item d'inventaire peut être contenu dans un autre (sac/lot).
--       parent_id pointe vers la ligne « contenant ». Suppression du
--       contenant → suppression de son contenu (CASCADE). Un seul niveau
--       (contrôlé applicativement).
-- =====================================================================

ALTER TABLE sp_inventaire_item
    ADD COLUMN parent_id UUID REFERENCES sp_inventaire_item(id) ON DELETE CASCADE;

CREATE INDEX idx_inventaire_item_parent ON sp_inventaire_item (parent_id);

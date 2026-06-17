-- Type de fonction ciblé pour la condition QUALIF_TYPE_COUNT (CHEF_AGRES / CONDUCTEUR /
-- CHEF_EQUIPE / EQUIPIER). Null pour les autres conditions.
ALTER TABLE sp_badge
    ADD COLUMN type_fonction VARCHAR(20);

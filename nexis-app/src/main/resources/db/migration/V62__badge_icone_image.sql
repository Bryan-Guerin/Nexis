-- Image-icône optionnelle sur un badge (remplace l'emoji si définie). FK vers la
-- bibliothèque sp_icone ; ON DELETE SET NULL → si l'image est supprimée, le badge
-- repasse à son emoji. Une même image peut être référencée par plusieurs badges.
ALTER TABLE sp_badge
    ADD COLUMN icone_image_id UUID REFERENCES sp_icone(id) ON DELETE SET NULL;

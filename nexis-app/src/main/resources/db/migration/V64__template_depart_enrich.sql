-- Enrichit une ligne de lot de départ : description libre + image-icône optionnelle
-- (FK sp_icone, ON DELETE SET NULL). Exploitées à l'affichage du lot et par le futur
-- moteur de questionnaire dispatch.
ALTER TABLE sp_template_depart
    ADD COLUMN description    TEXT,
    ADD COLUMN icone_image_id UUID REFERENCES sp_icone(id) ON DELETE SET NULL;

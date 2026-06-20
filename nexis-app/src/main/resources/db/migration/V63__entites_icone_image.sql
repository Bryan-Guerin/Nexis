-- Image-icône optionnelle (FK sp_icone, ON DELETE SET NULL, réutilisable) sur les natures
-- d'intervention, les fonctions d'organigramme et les types de véhicule. Repli sur l'emoji
-- existant si non définie.
ALTER TABLE sp_nature_intervention
    ADD COLUMN icone_image_id UUID REFERENCES sp_icone(id) ON DELETE SET NULL;
ALTER TABLE sp_fonction_orga
    ADD COLUMN icone_image_id UUID REFERENCES sp_icone(id) ON DELETE SET NULL;
ALTER TABLE sp_vehicule_type
    ADD COLUMN icone_image_id UUID REFERENCES sp_icone(id) ON DELETE SET NULL;

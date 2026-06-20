-- Moteur de couverture v1.8 : flag SR (secours routier / AVP), lots de départ déclenchés par un
-- flag (en plus de la nature), et reco de véhicule par unité portée par une question (ex. 1 VSAV
-- par victime). Le lot proposé à la création = union des lots des déclencheurs actifs.

-- Flag secours routier (AVP) sur l'intervention (comme incendie / véhicule impliqué).
ALTER TABLE sp_intervention ADD COLUMN sr BOOLEAN NOT NULL DEFAULT FALSE;

-- Lot de départ déclenché soit par une nature, soit par un flag (exclusif).
ALTER TABLE sp_template_depart ALTER COLUMN nature_id DROP NOT NULL;
ALTER TABLE sp_template_depart ADD COLUMN declencheur_flag VARCHAR(30);   -- INCENDIE | SR | VEHICULE_IMPLIQUE
ALTER TABLE sp_template_depart DROP CONSTRAINT sp_template_depart_nature_id_vehicule_type_id_key;
ALTER TABLE sp_template_depart
    ADD CONSTRAINT ck_template_declencheur CHECK ((nature_id IS NOT NULL) <> (declencheur_flag IS NOT NULL));
CREATE UNIQUE INDEX uq_template_nature ON sp_template_depart (nature_id, vehicule_type_id) WHERE nature_id IS NOT NULL;
CREATE UNIQUE INDEX uq_template_flag   ON sp_template_depart (declencheur_flag, vehicule_type_id) WHERE declencheur_flag IS NOT NULL;

-- Reco de véhicule portée par une question (ex. question NB_VICTIMES → 1 VSAV par victime).
ALTER TABLE sp_question ADD COLUMN reco_vehicule_type_id UUID REFERENCES sp_vehicule_type(id) ON DELETE SET NULL;
ALTER TABLE sp_question ADD COLUMN reco_par_unite BOOLEAN NOT NULL DEFAULT FALSE;

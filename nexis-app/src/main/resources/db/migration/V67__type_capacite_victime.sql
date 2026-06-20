-- Capacité victime d'un type de véhicule (nb de victimes transportables, ex. VSAV = 1).
-- Sert au questionnaire dispatch : à l'engagement du lot, les types porteurs de victimes
-- sont dimensionnés pour couvrir le nombre de victimes saisi. 0 = ne transporte pas de victime.
ALTER TABLE sp_vehicule_type
    ADD COLUMN capacite_victime INT NOT NULL DEFAULT 0;

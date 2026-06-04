-- =====================================================================
-- V4 : référentiels grades (GN/SP), fonctions SP, refactoring membres/postes
-- =====================================================================

-- Grades Gendarmerie Nationale
CREATE TABLE gn_grade (
    id    UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code  VARCHAR(30)  NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL
);

-- Grades Sapeurs-Pompiers
CREATE TABLE sp_grade (
    id    UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code  VARCHAR(30)  NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL
);

-- Fonctions SP (qualification technique par poste)
CREATE TABLE sp_fonction (
    id    UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code  VARCHAR(30)  NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL
);

-- gn_membre : grade libre → FK gn_grade
ALTER TABLE gn_membre ADD COLUMN grade_id UUID REFERENCES gn_grade(id);
ALTER TABLE gn_membre DROP COLUMN grade;

-- sp_membre : grade libre → FK sp_grade
ALTER TABLE sp_membre ADD COLUMN grade_id UUID REFERENCES sp_grade(id);
ALTER TABLE sp_membre DROP COLUMN grade;

-- sp_vehicule_type_poste : code/label poste libre → FK sp_fonction
ALTER TABLE sp_vehicule_type_poste ADD COLUMN fonction_id UUID REFERENCES sp_fonction(id);
ALTER TABLE sp_vehicule_type_poste DROP CONSTRAINT IF EXISTS sp_vehicule_type_poste_vehicule_type_id_code_poste_key;
ALTER TABLE sp_vehicule_type_poste DROP COLUMN code_poste;
ALTER TABLE sp_vehicule_type_poste DROP COLUMN label_poste;
ALTER TABLE sp_vehicule_type_poste ADD CONSTRAINT sp_vtp_unique_type_fonction UNIQUE (vehicule_type_id, fonction_id);

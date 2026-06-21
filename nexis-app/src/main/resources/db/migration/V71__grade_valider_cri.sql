-- Flag par grade autorisant la validation des CRI (sergent et +). Configurable depuis l'admin.
ALTER TABLE sp_grade
    ADD COLUMN peut_valider_cri BOOLEAN NOT NULL DEFAULT FALSE;

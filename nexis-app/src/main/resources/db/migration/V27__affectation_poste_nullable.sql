-- Préserver l'historique des affectations : supprimer un poste (ou une fonction)
-- ne doit plus être bloqué par la FK, ni effacer les affectations.
-- On rend poste_id nullable et on passe la FK en ON DELETE SET NULL :
-- les affectations sont « détachées » (poste_id = NULL) et l'historique est conservé.

ALTER TABLE sp_vehicule_affectation ALTER COLUMN poste_id DROP NOT NULL;

ALTER TABLE sp_vehicule_affectation DROP CONSTRAINT sp_vehicule_affectation_poste_id_fkey;

ALTER TABLE sp_vehicule_affectation
    ADD CONSTRAINT sp_vehicule_affectation_poste_id_fkey
    FOREIGN KEY (poste_id) REFERENCES sp_vehicule_type_poste (id) ON DELETE SET NULL;

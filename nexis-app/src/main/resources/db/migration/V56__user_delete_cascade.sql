-- Permet la suppression d'un utilisateur (ROLE_SYSTEM) : la fiche membre de chaque
-- faction et tout son historique sont supprimés en cascade. On bascule en ON DELETE
-- CASCADE les FK qui étaient en RESTRICT (ou sans action) et qui bloquaient la suppression.

-- ref_user → fiches membres
ALTER TABLE sp_membre DROP CONSTRAINT sp_membre_user_id_fkey,
    ADD CONSTRAINT sp_membre_user_id_fkey FOREIGN KEY (user_id) REFERENCES ref_user(id) ON DELETE CASCADE;
ALTER TABLE gn_membre DROP CONSTRAINT gn_membre_user_id_fkey,
    ADD CONSTRAINT gn_membre_user_id_fkey FOREIGN KEY (user_id) REFERENCES ref_user(id) ON DELETE CASCADE;

-- sp_membre → affectations & versements de paie
ALTER TABLE sp_vehicule_affectation DROP CONSTRAINT sp_vehicule_affectation_membre_id_fkey,
    ADD CONSTRAINT sp_vehicule_affectation_membre_id_fkey FOREIGN KEY (membre_id) REFERENCES sp_membre(id) ON DELETE CASCADE;
ALTER TABLE sp_paie_versement DROP CONSTRAINT sp_paie_versement_membre_id_fkey,
    ADD CONSTRAINT sp_paie_versement_membre_id_fkey FOREIGN KEY (membre_id) REFERENCES sp_membre(id) ON DELETE CASCADE;

-- gn_membre → affectations
ALTER TABLE gn_vehicule_affectation DROP CONSTRAINT gn_vehicule_affectation_membre_id_fkey,
    ADD CONSTRAINT gn_vehicule_affectation_membre_id_fkey FOREIGN KEY (membre_id) REFERENCES gn_membre(id) ON DELETE CASCADE;

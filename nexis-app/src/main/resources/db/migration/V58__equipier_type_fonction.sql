-- Type de fonction tenu par l'équipier sur l'intervention (CHEF_AGRES / CONDUCTEUR /
-- CHEF_EQUIPE / EQUIPIER), figé au snapshot. Permet de compter les interventions d'un
-- membre PAR rôle tenu (badge « X interventions en tant que chef d'agrès », etc.).
ALTER TABLE sp_intervention_equipier
    ADD COLUMN type_fonction VARCHAR(20);

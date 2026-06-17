-- Identifiant du membre figé sur l'équipier historisé (UUID simple, SANS FK : l'archive
-- survit à la suppression du membre). Permet de recompter les interventions d'un effectif
-- après clôture, l'engin live n'étant plus rattaché.
ALTER TABLE sp_intervention_equipier
    ADD COLUMN membre_id UUID;
CREATE INDEX idx_sp_int_equipier_membre ON sp_intervention_equipier(membre_id);

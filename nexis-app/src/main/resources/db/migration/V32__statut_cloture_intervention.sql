-- Clôture automatique d'intervention pilotée par statut : l'intervention se clôture quand
-- TOUS ses engins portent un statut dont la case « clôture intervention » est cochée.
-- Permet un statut « Disponible radio » qui libère le véhicule SANS fermer l'intervention.
ALTER TABLE sp_vehicule_statut ADD COLUMN cloture_intervention BOOLEAN NOT NULL DEFAULT FALSE;

-- Comportement existant conservé : les statuts liés à l'état DISPONIBLE valident la clôture.
UPDATE sp_vehicule_statut s SET cloture_intervention = TRUE
WHERE EXISTS (SELECT 1 FROM sp_vehicule_etat e WHERE e.id = s.etat_id AND e.code = 'DISPONIBLE');

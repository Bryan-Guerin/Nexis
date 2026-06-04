-- =====================================================================
-- V23 : disponibilité planning (fin de garde anticipée) + taux astreinte
-- =====================================================================

-- Horodatage de départ effectif : la plage reste comptée jusqu'à sa fin (paie),
-- mais le membre n'est plus "en service" / dispatchable à partir de quitte_le.
ALTER TABLE sp_planning ADD COLUMN quitte_le TIMESTAMPTZ;

-- Taux horaire d'astreinte (paie), en euros.
ALTER TABLE sp_grade ADD COLUMN taux_astreinte NUMERIC(8,2) NOT NULL DEFAULT 0;

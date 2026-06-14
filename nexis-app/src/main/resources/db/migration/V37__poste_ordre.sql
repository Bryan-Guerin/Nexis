-- =====================================================================
-- V37 : ordre d'affichage des postes au sein d'un type de véhicule
--       (réordonnable ; pilote aussi l'ordre de l'équipage au dispatch).
--       Backfill : ordre stable par type sur l'existant.
-- =====================================================================

ALTER TABLE sp_vehicule_type_poste ADD COLUMN ordre INT NOT NULL DEFAULT 0;

WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY vehicule_type_id ORDER BY id) - 1 AS rn
      FROM sp_vehicule_type_poste
)
UPDATE sp_vehicule_type_poste p
   SET ordre = r.rn
  FROM ranked r
 WHERE p.id = r.id;

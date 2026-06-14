-- =====================================================================
-- V35 : nature « principale » d'un type de véhicule (étoile). Sert de
--       catégorie de regroupement au dispatch. Backfill : un type qui n'a
--       qu'une seule nature voit celle-ci promue principale automatiquement.
-- =====================================================================

ALTER TABLE sp_vehicule_type
    ADD COLUMN nature_principale_id UUID REFERENCES sp_nature_intervention(id);

UPDATE sp_vehicule_type t
   SET nature_principale_id = sub.nature_id
  FROM (
        SELECT type_id, MIN(nature_id) AS nature_id
          FROM sp_vehicule_type_nature
      GROUP BY type_id
        HAVING COUNT(*) = 1
       ) sub
 WHERE t.id = sub.type_id;

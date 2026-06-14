-- =====================================================================
-- V35 : nature « principale » d'un type de véhicule (étoile). Sert de
--       catégorie de regroupement au dispatch. Backfill : un type qui n'a
--       qu'une seule nature voit celle-ci promue principale automatiquement.
-- =====================================================================

ALTER TABLE sp_vehicule_type
    ADD COLUMN nature_principale_id UUID REFERENCES sp_nature_intervention(id);

UPDATE sp_vehicule_type t
   SET nature_principale_id = n.nature_id
  FROM sp_vehicule_type_nature n
 WHERE t.id = n.type_id
   AND n.type_id IN (
        SELECT type_id FROM sp_vehicule_type_nature
      GROUP BY type_id HAVING COUNT(*) = 1
       );

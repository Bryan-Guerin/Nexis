-- =====================================================================
-- V33 : suppression des statuts de planning « Formation » et « Absent »
--       (non pertinents pour le planning de service). Les plages qui les
--       référencent sont supprimées au préalable (FK).
-- =====================================================================

DELETE FROM sp_planning
 WHERE statut_id IN (SELECT id FROM sp_planning_statut WHERE code IN ('FORMATION', 'ABSENT'));
DELETE FROM gn_planning
 WHERE statut_id IN (SELECT id FROM gn_planning_statut WHERE code IN ('FORMATION', 'ABSENT'));

DELETE FROM sp_planning_statut WHERE code IN ('FORMATION', 'ABSENT');
DELETE FROM gn_planning_statut WHERE code IN ('FORMATION', 'ABSENT');

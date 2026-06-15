-- =====================================================================
-- V39 : catégorie d'une fonction (Chef d'agrès / Conducteur / Chef d'équipe
--       / Équipier) → ordre d'affichage de l'équipage au dispatch.
--       Backfill best-effort sur le libellé/code ; défaut EQUIPIER.
-- =====================================================================

ALTER TABLE sp_fonction ADD COLUMN type_fonction VARCHAR(20) NOT NULL DEFAULT 'EQUIPIER';

UPDATE sp_fonction SET type_fonction = CASE
    WHEN lower(label) LIKE '%chef%agr%'                              THEN 'CHEF_AGRES'
    WHEN lower(label) LIKE '%conducteur%' OR upper(code) IN ('COND', 'CO', 'CDT') THEN 'CONDUCTEUR'
    WHEN lower(label) LIKE '%chef%équipe%' OR lower(label) LIKE '%chef%equipe%' THEN 'CHEF_EQUIPE'
    ELSE 'EQUIPIER'
END;

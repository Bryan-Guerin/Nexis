-- =====================================================================
-- V18 : rôles Dispatch & RH (SP) + taux horaire sur le grade (paie)
-- =====================================================================

-- Nouveaux rôles, rattachés à ROLE_ADMIN_SP (l'admin SP les couvre par héritage).
-- Un référent RH se voit attribuer ROLE_SP + ROLE_SP_RH.
INSERT INTO ref_role (code, label, parent_id)
    SELECT 'ROLE_SP_DISPATCH', 'Dispatcher SP', id FROM ref_role WHERE code = 'ROLE_ADMIN_SP';

INSERT INTO ref_role (code, label, parent_id)
    SELECT 'ROLE_SP_RH', 'RH Sapeurs-Pompiers', id FROM ref_role WHERE code = 'ROLE_ADMIN_SP';

-- Taux horaire par grade (base de la paie), en euros.
ALTER TABLE sp_grade ADD COLUMN taux_horaire NUMERIC(8,2) NOT NULL DEFAULT 0;

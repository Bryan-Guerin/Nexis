-- Catalogue de badges par défaut. ON CONFLICT (code) DO NOTHING : ne réécrit pas
-- un badge déjà créé manuellement avec le même code.

-- ── Paliers : interventions, gardes, ancienneté, grade, qualifications ──────────
INSERT INTO sp_badge (id, code, label, icone, type_condition, nature_id, type_fonction, seuil, xp_reward, ordre) VALUES
  -- Interventions (INTER_COUNT)
  (gen_random_uuid(), 'INTER_1',    'Baptême du feu',         '🚒', 'INTER_COUNT', NULL, NULL,   1,  25,  0),
  (gen_random_uuid(), 'INTER_10',   'Soldat du feu',          '🎖️', 'INTER_COUNT', NULL, NULL,  10,  50,  1),
  (gen_random_uuid(), 'INTER_50',   'Vétéran',                '🏅', 'INTER_COUNT', NULL, NULL,  50, 100,  2),
  (gen_random_uuid(), 'INTER_100',  'Légende de la caserne',  '🏆', 'INTER_COUNT', NULL, NULL, 100, 200,  3),
  (gen_random_uuid(), 'INTER_250',  'Pilier opérationnel',    '👑', 'INTER_COUNT', NULL, NULL, 250, 400,  4),
  -- Gardes (GARDE_HEURES)
  (gen_random_uuid(), 'GARDE_1',    'Première garde',         '⏱️', 'GARDE_HEURES', NULL, NULL,    1,  25, 10),
  (gen_random_uuid(), 'GARDE_24',   'Garde assidu',           '🕐', 'GARDE_HEURES', NULL, NULL,   24,  50, 11),
  (gen_random_uuid(), 'GARDE_100',  'Sentinelle',             '🛡️', 'GARDE_HEURES', NULL, NULL,  100, 100, 12),
  (gen_random_uuid(), 'GARDE_500',  'Gardien infatigable',    '⚔️', 'GARDE_HEURES', NULL, NULL,  500, 250, 13),
  (gen_random_uuid(), 'GARDE_1000', 'Permanence d''honneur',  '🌟', 'GARDE_HEURES', NULL, NULL, 1000, 500, 14),
  -- Ancienneté (SERVICE_JOURS)
  (gen_random_uuid(), 'ANCIEN_1',   'Recrue',                 '🐣', 'SERVICE_JOURS', NULL, NULL,   1,  10, 20),
  (gen_random_uuid(), 'ANCIEN_30',  'Engagé',                 '📅', 'SERVICE_JOURS', NULL, NULL,  30,  50, 21),
  (gen_random_uuid(), 'ANCIEN_180', 'Ancien',                 '🗓️', 'SERVICE_JOURS', NULL, NULL, 180, 100, 22),
  (gen_random_uuid(), 'ANCIEN_365', 'Pilier historique',      '🎂', 'SERVICE_JOURS', NULL, NULL, 365, 200, 23),
  -- Grade (GRADE_JOURS)
  (gen_random_uuid(), 'GRADE_50',   'Installé dans le grade', '🎗️', 'GRADE_JOURS', NULL, NULL,  50,  50, 30),
  (gen_random_uuid(), 'GRADE_180',  'Référent de grade',      '📌', 'GRADE_JOURS', NULL, NULL, 180, 100, 31),
  -- Qualifications — nombre total (QUALIF_COUNT)
  (gen_random_uuid(), 'QUAL_6',     'Formation initiale validée', '🎓', 'QUALIF_COUNT', NULL, NULL,  6,  75, 40),
  (gen_random_uuid(), 'QUAL_10',    'Spécialiste',            '🧰', 'QUALIF_COUNT', NULL, NULL, 10, 150, 41),
  (gen_random_uuid(), 'QUAL_15',    'Expert polyvalent',      '🛠️', 'QUALIF_COUNT', NULL, NULL, 15, 250, 42),
  (gen_random_uuid(), 'QUAL_20',    'Maître toutes fonctions','🏵️', 'QUALIF_COUNT', NULL, NULL, 20, 400, 43),
  -- Qualifications — par type de fonction (QUALIF_TYPE_COUNT)
  (gen_random_uuid(), 'QUAL_CA_1',   'Chef d''agrès',         '🎯', 'QUALIF_TYPE_COUNT', NULL, 'CHEF_AGRES', 1, 100, 50),
  (gen_random_uuid(), 'QUAL_COND_1', 'Conducteur',            '🚐', 'QUALIF_TYPE_COUNT', NULL, 'CONDUCTEUR', 1,  75, 51),
  (gen_random_uuid(), 'QUAL_COND_3', 'Conducteur confirmé',   '🚒', 'QUALIF_TYPE_COUNT', NULL, 'CONDUCTEUR', 3, 150, 52)
ON CONFLICT (code) DO NOTHING;

-- ── « Première fois » par nature d'intervention, avec paliers 1 / 10 / 50 / 100 ──
-- Une ligne par (nature × palier). Icône = celle de la nature (sinon 🔥).
-- Code : NAT_<code nature>_<palier>. Si aucune nature configurée, n'insère rien.
INSERT INTO sp_badge (id, code, label, icone, type_condition, nature_id, type_fonction, seuil, xp_reward, ordre)
SELECT gen_random_uuid(),
       'NAT_' || n.code || '_' || t.seuil,
       n.label || ' ×' || t.seuil,
       COALESCE(NULLIF(n.icone, ''), '🔥'),
       'INTER_NATURE_COUNT',
       n.id,
       NULL,
       t.seuil,
       t.xp,
       100 + t.rang
FROM sp_nature_intervention n
CROSS JOIN (VALUES (1, 25, 0), (10, 50, 1), (50, 100, 2), (100, 200, 3)) AS t(seuil, xp, rang)
ON CONFLICT (code) DO NOTHING;

-- Badges « intervention de la semaine » (INTER_SEMAINE_COUNT) : nombre de fois où le membre
-- a participé à l'intervention gagnante du vote hebdomadaire. Seuils 1 / 5 / 10.
-- Les gagnantes passées sont recalculées depuis les votes (pas de persistance dédiée).
-- ON CONFLICT (code) DO NOTHING : ne réécrit pas un badge déjà créé manuellement.
INSERT INTO sp_badge (id, code, label, icone, type_condition, nature_id, type_fonction, seuil, xp_reward, ordre) VALUES
  (gen_random_uuid(), 'SEMAINE_1',  'Élu de la semaine',   '⭐', 'INTER_SEMAINE_COUNT', NULL, NULL,  1,  50, 5),
  (gen_random_uuid(), 'SEMAINE_5',  'Habitué du podium',   '🌟', 'INTER_SEMAINE_COUNT', NULL, NULL,  5, 150, 6),
  (gen_random_uuid(), 'SEMAINE_10', 'Star de la caserne',  '🏆', 'INTER_SEMAINE_COUNT', NULL, NULL, 10, 300, 7)
ON CONFLICT (code) DO NOTHING;

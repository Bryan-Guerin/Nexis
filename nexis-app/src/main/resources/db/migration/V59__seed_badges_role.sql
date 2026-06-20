-- Badges « interventions tenues dans un rôle » (INTER_TYPE_FONCTION_COUNT).
-- Paliers 10 / 50 / 100 par type de fonction. Le rôle est figé au snapshot de
-- l'équipage (colonne sp_intervention_equipier.type_fonction, V58) : seules les
-- interventions clôturées APRÈS la V58 comptent (+ les interventions ouvertes, lues en live).
-- ON CONFLICT (code) DO NOTHING : ne réécrit pas un badge déjà créé manuellement.
INSERT INTO sp_badge (id, code, label, icone, type_condition, nature_id, type_fonction, seuil, xp_reward, ordre)
SELECT gen_random_uuid(),
       'ROLE_' || r.tf || '_' || t.seuil,
       r.lbl || ' ×' || t.seuil,
       r.ico,
       'INTER_TYPE_FONCTION_COUNT',
       NULL,
       r.tf,
       t.seuil,
       t.xp,
       r.base + t.rang
FROM (VALUES
        ('CHEF_AGRES',  'Chef d''agrès',  '🎯',   60),
        ('CONDUCTEUR',  'Conducteur',     '🚒',   63),
        ('CHEF_EQUIPE', 'Chef d''équipe', '🧭',   66),
        ('EQUIPIER',    'Équipier',       '🧑‍🚒', 69)
     ) AS r(tf, lbl, ico, base)
CROSS JOIN (VALUES (10, 75, 0), (50, 150, 1), (100, 300, 2)) AS t(seuil, xp, rang)
ON CONFLICT (code) DO NOTHING;

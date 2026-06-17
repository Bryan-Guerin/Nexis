-- Organigramme par défaut : Chef de centre (racine) + 5 services rattachés.
-- ON CONFLICT (code) DO NOTHING : ne réécrit pas une fonction déjà créée.

-- Racine
INSERT INTO sp_fonction_orga (id, code, label, parent_id, ordre, icone) VALUES
  (gen_random_uuid(), 'CHEF_CENTRE', 'Chef de centre', NULL, 0, '⭐')
ON CONFLICT (code) DO NOTHING;

-- Services rattachés au Chef de centre
INSERT INTO sp_fonction_orga (id, code, label, parent_id, ordre, icone)
SELECT gen_random_uuid(), c.code, c.label,
       (SELECT id FROM sp_fonction_orga WHERE code = 'CHEF_CENTRE'),
       c.ordre, c.icone
FROM (VALUES
  ('CHEF_SSSM',         'Chef SSSM',         1, '⚕️'),
  ('SERVICE_RH',        'Service RH',        2, '👥'),
  ('SERVICE_PREVISION', 'Service Prévision', 3, '🗺️'),
  ('SERVICE_FORMATION', 'Service Formation', 4, '🎓'),
  ('CTA_CODIS',         'CTA Codis',         5, '📞')
) AS c(code, label, ordre, icone)
ON CONFLICT (code) DO NOTHING;

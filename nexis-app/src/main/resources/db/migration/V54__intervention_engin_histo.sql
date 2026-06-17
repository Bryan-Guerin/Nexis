-- Historisation de l'engagement : à la clôture d'une intervention, on fige les engins
-- (libellé + type, en texte) et leur équipage (matricule, nom, grade, poste). Cela
-- découple l'archive de la FK véhicule (suppression d'un véhicule sans perte d'historique)
-- et conserve « qui est intervenu », perdu autrement à la fermeture des affectations.

CREATE TABLE sp_intervention_engin (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intervention_id UUID NOT NULL REFERENCES sp_intervention(id) ON DELETE CASCADE,
    libelle         VARCHAR(100) NOT NULL,
    type_code       VARCHAR(30),
    ordre           INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_sp_int_engin_inter ON sp_intervention_engin(intervention_id);

CREATE TABLE sp_intervention_equipier (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    engin_id  UUID NOT NULL REFERENCES sp_intervention_engin(id) ON DELETE CASCADE,
    matricule VARCHAR(20),
    nom       VARCHAR(100),
    grade     VARCHAR(100),
    poste     VARCHAR(100),
    ordre     INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_sp_int_equipier_engin ON sp_intervention_equipier(engin_id);

-- Backfill des interventions déjà clôturées : on fige le libellé + le type des engins
-- depuis la table de liaison. L'équipage est déjà perdu (affectations fermées) → vide.
INSERT INTO sp_intervention_engin (id, intervention_id, libelle, type_code, ordre)
SELECT gen_random_uuid(), iv.intervention_id, v.libelle, t.code, 0
FROM sp_intervention_vehicule iv
JOIN sp_intervention i ON i.id = iv.intervention_id
JOIN sp_vehicule v      ON v.id = iv.vehicule_id
LEFT JOIN sp_vehicule_type t ON t.id = v.type_id
WHERE i.fin IS NOT NULL;

-- Retire la FK véhicule des interventions clôturées : l'historique vit désormais dans
-- le snapshot, et un véhicule peut être supprimé sans casser les archives.
DELETE FROM sp_intervention_vehicule
WHERE intervention_id IN (SELECT id FROM sp_intervention WHERE fin IS NOT NULL);

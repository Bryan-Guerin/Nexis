-- V6 : ajout contrat (SPP/SPV), numéro de casier et compteur matricule sur sp_membre
-- Le matricule devient auto-calculé : {CONTRAT}-{NNN} avec NNN commun SPP+SPV débutant à 352.

ALTER TABLE sp_membre
    ADD COLUMN contrat        VARCHAR(3)  NOT NULL DEFAULT 'SPV',
    ADD COLUMN numero_casier  SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN numero_compteur INT;

-- Attribution séquentielle aux membres existants (départ 352, ordre déterministe par id)
WITH ranked AS (
    SELECT id,
           (ROW_NUMBER() OVER (ORDER BY id) + 351)::INT AS n
    FROM sp_membre
)
UPDATE sp_membre
SET numero_compteur = ranked.n
FROM ranked
WHERE sp_membre.id = ranked.id;

-- Mise à jour du matricule au nouveau format SPP-352 / SPV-353 …
UPDATE sp_membre
SET matricule = contrat || '-' || LPAD(numero_compteur::TEXT, 3, '0');

-- Contraintes finales
ALTER TABLE sp_membre
    ALTER COLUMN numero_compteur SET NOT NULL,
    ADD CONSTRAINT sp_membre_numero_compteur_unique UNIQUE (numero_compteur);

-- Suppression du DEFAULT temporaire sur contrat (les nouvelles lignes l'imposent via le service)
ALTER TABLE sp_membre ALTER COLUMN contrat DROP DEFAULT;

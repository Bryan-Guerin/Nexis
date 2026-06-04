-- =====================================================================
-- V15 : refonte de l'inventaire.
--   - Catalogue d'objets configurable.
--   - Items du type = objet + quantité attendue.
--   - Vérification = quantités constatées + conformité (→ statut véhicule).
-- =====================================================================

-- Catalogue d'objets d'inventaire
CREATE TABLE sp_objet_inventaire (
    id    UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code  VARCHAR(30)  NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    ordre INT          NOT NULL DEFAULT 0
);
INSERT INTO sp_objet_inventaire (code, label, ordre) VALUES
    ('BRANCARD',   'Brancard',        0),
    ('DEFIB',      'Défibrillateur',  1),
    ('O2',         'Bouteille O2',    2),
    ('BALISAGE',   'Lot de balisage', 3),
    ('EXTINCTEUR', 'Extincteur',      4);

-- Items du modèle d'inventaire (porté par le type) : objet + quantité attendue
DELETE FROM sp_inventaire_item;
ALTER TABLE sp_inventaire_item DROP COLUMN libelle;
ALTER TABLE sp_inventaire_item ADD COLUMN objet_id UUID NOT NULL REFERENCES sp_objet_inventaire(id) ON DELETE CASCADE;
ALTER TABLE sp_inventaire_item ADD COLUMN quantite INT NOT NULL DEFAULT 1;

-- Vérifications : conformité globale + quantités par ligne (historique réinitialisé)
DELETE FROM sp_verification_ligne;
DELETE FROM sp_verification;
ALTER TABLE sp_verification ADD COLUMN conforme BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE sp_verification_ligne DROP COLUMN present;
ALTER TABLE sp_verification_ligne DROP COLUMN commentaire;
ALTER TABLE sp_verification_ligne ADD COLUMN quantite_attendue INT NOT NULL DEFAULT 1;
ALTER TABLE sp_verification_ligne ADD COLUMN quantite_presente INT NOT NULL DEFAULT 0;
ALTER TABLE sp_verification_ligne ADD COLUMN conforme BOOLEAN NOT NULL DEFAULT TRUE;

-- Statut "Indisponible" appliqué quand une vérification est non conforme (KO)
INSERT INTO sp_vehicule_etat (code, label, couleur, ordre)
VALUES ('INDISPONIBLE', 'Indisponible', '#e05c5c', (SELECT COALESCE(MAX(ordre), -1) + 1 FROM sp_vehicule_etat));

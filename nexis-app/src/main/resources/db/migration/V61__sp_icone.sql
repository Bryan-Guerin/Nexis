-- Bibliothèque d'images-icônes réutilisables (badges, natures, fonctions orga, types de
-- véhicule…), stockées en base (bytea), sur le modèle de sp_document. Les entités à icône
-- référencent une sp_icone via une FK nullable, en repli sur l'emoji existant.
CREATE TABLE sp_icone (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nom          VARCHAR(120) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    taille       BIGINT       NOT NULL,
    contenu      BYTEA        NOT NULL,
    cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cree_par     VARCHAR(50)
);

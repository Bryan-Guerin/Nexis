-- Fréquences radio : référentiel simple géré par les admins SP, affiché à tous les SP
-- (panneau du tableau de bord). Format de fréquence libre, ex. "150.1".
CREATE TABLE sp_frequence_radio (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    description VARCHAR(100) NOT NULL,
    frequence   VARCHAR(20)  NOT NULL,
    ordre       INT          NOT NULL DEFAULT 0
);

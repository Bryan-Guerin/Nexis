-- Événements SP : créés par les admins (titre + texte + date). Chaque effectif peut
-- déclarer sa présence (oui/non). Les non-répondants ne sont pas tracés.
CREATE TABLE sp_evenement (
    id             UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    titre          VARCHAR(120) NOT NULL,
    texte          TEXT,
    date_evenement TIMESTAMPTZ  NOT NULL,
    cree_par       VARCHAR(50),
    cree_le        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE sp_evenement_reponse (
    id           UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    evenement_id UUID    NOT NULL REFERENCES sp_evenement(id) ON DELETE CASCADE,
    membre_id    UUID    NOT NULL REFERENCES sp_membre(id)    ON DELETE CASCADE,
    present      BOOLEAN NOT NULL,
    repondu_le   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (evenement_id, membre_id)
);

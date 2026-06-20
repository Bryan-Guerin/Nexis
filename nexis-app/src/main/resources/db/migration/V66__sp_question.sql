-- Questionnaire guidé du dispatcher (moteur configurable). Liste ordonnée de questions à
-- conditions : chaque réponse peut préremplir un champ de l'intervention (cible) et/ou
-- suggérer une nature (et donc son lot de départ). Une question peut n'apparaître que si une
-- question parente (OUI_NON) a la réponse attendue → enchaînement.
CREATE TABLE sp_question (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    libelle               VARCHAR(200) NOT NULL,
    type                  VARCHAR(20)  NOT NULL,            -- OUI_NON | NOMBRE
    ordre                 INT          NOT NULL DEFAULT 0,
    cible                 VARCHAR(30)  NOT NULL DEFAULT 'AUCUNE',
    nature_suggeree_id    UUID REFERENCES sp_nature_intervention(id) ON DELETE SET NULL,
    condition_question_id UUID REFERENCES sp_question(id)            ON DELETE SET NULL,
    condition_attendue    BOOLEAN      NOT NULL DEFAULT TRUE
);

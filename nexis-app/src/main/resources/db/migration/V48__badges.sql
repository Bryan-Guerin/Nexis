-- Catalogue des badges (succès) attribués automatiquement aux membres SP selon
-- des conditions (nombre d'interventions, heures de garde, ancienneté…).
-- Visible par tous, attribution non révocable (succès historique).
CREATE TABLE sp_badge (
    id              UUID PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL UNIQUE,
    label           VARCHAR(120) NOT NULL,
    icone           VARCHAR(8),
    description     TEXT,
    type_condition  VARCHAR(30)  NOT NULL,        -- INTER_COUNT | INTER_NATURE_COUNT | GARDE_HEURES | SERVICE_JOURS | GRADE_JOURS
    nature_id       UUID         REFERENCES sp_nature_intervention(id) ON DELETE SET NULL,
    seuil           INT          NOT NULL,
    xp_reward       INT          NOT NULL DEFAULT 0,
    ordre           INT          NOT NULL DEFAULT 0
);

-- Obtention : un membre peut obtenir un badge une seule fois (UNIQUE).
CREATE TABLE sp_membre_badge (
    id          UUID PRIMARY KEY,
    membre_id   UUID NOT NULL REFERENCES sp_membre(id) ON DELETE CASCADE,
    badge_id    UUID NOT NULL REFERENCES sp_badge(id)  ON DELETE CASCADE,
    obtenu_le   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (membre_id, badge_id)
);
CREATE INDEX idx_sp_mb_membre ON sp_membre_badge(membre_id);
CREATE INDEX idx_sp_mb_badge  ON sp_membre_badge(badge_id);

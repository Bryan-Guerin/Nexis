-- Vote "intervention de la semaine" : chaque SP actif vote 1× par semaine pour
-- une intervention clôturée de la semaine précédente. semaine_date = lundi de
-- la semaine OÙ s'est déroulée l'intervention (pas celle du vote).
CREATE TABLE sp_intervention_vote (
    id              UUID PRIMARY KEY,
    intervention_id UUID NOT NULL REFERENCES sp_intervention(id) ON DELETE CASCADE,
    membre_id       UUID NOT NULL REFERENCES sp_membre(id)       ON DELETE CASCADE,
    semaine_date    DATE NOT NULL,                                    -- lundi de la semaine concernée
    vote_le         TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (membre_id, semaine_date)                                  -- 1 vote / membre / semaine
);
CREATE INDEX idx_sp_ivote_inter   ON sp_intervention_vote(intervention_id);
CREATE INDEX idx_sp_ivote_semaine ON sp_intervention_vote(semaine_date);

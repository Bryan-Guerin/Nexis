-- Affectation forcée par le dispatch : bypass du check de qualification.
-- Traçabilité : qui a forcé, quand.
ALTER TABLE sp_vehicule_affectation
    ADD COLUMN forcee    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN force_par TEXT,
    ADD COLUMN force_le  TIMESTAMP;

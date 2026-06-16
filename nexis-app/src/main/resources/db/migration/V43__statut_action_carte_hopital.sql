-- Action « carte » portée par le statut RP (transport hôpital, sur place, retour caserne…).
ALTER TABLE sp_vehicule_statut
    ADD COLUMN action_carte VARCHAR(20) NOT NULL DEFAULT 'AUCUNE';

-- Référentiel hôpitaux (destinations de transport, repères carte).
CREATE TABLE sp_hopital (
    id          UUID PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL UNIQUE,
    label       VARCHAR(100) NOT NULL,
    ordre       INT          NOT NULL,
    coordonnees VARCHAR(6)
);

-- Destination hôpital courante d'un véhicule (transport en cours).
ALTER TABLE sp_vehicule
    ADD COLUMN hopital_destination_id UUID REFERENCES sp_hopital(id);

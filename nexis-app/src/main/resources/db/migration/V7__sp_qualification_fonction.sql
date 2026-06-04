-- =====================================================================
-- V7 : la qualification d'un membre porte sur une FONCTION, pas sur un
--      poste-de-type. On remplace sp_qualification(membre_id, poste_id)
--      par sp_membre_qualification(membre_id, fonction_id).
--
--      Langage métier :
--        - Fonction          = catalogue des rôles (= des qualifications)
--        - Qualification     = un membre est habilité à une fonction (N–N)
--        - Poste (de type)   = requiert une fonction
--        - Affectation       = un membre occupe un poste d'un véhicule
-- =====================================================================

CREATE TABLE sp_membre_qualification (
    membre_id   UUID NOT NULL REFERENCES sp_membre(id)   ON DELETE CASCADE,
    fonction_id UUID NOT NULL REFERENCES sp_fonction(id) ON DELETE CASCADE,
    PRIMARY KEY (membre_id, fonction_id)
);

-- Reprise des données : chaque qualification (membre, poste-de-type) devient
-- (membre, fonction-requise-par-le-poste). DISTINCT car plusieurs postes de
-- types différents peuvent requérir la même fonction.
INSERT INTO sp_membre_qualification (membre_id, fonction_id)
SELECT DISTINCT q.membre_id, p.fonction_id
FROM sp_qualification q
JOIN sp_vehicule_type_poste p ON p.id = q.poste_id;

DROP TABLE sp_qualification;

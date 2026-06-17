-- Condition FONCTION_ORGA : badge attribué si le membre appartient à une fonction
-- d'organigramme donnée (ex. Service RH, Remise…). fonction_orga_id null sinon.
ALTER TABLE sp_badge
    ADD COLUMN fonction_orga_id UUID REFERENCES sp_fonction_orga(id) ON DELETE SET NULL;

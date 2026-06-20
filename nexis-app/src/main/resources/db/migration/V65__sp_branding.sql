-- Branding de l'instance (singleton) : logo de la caserne, choisi parmi la bibliothèque
-- d'images (sp_icone). ON DELETE SET NULL → si l'image est supprimée, le logo se vide
-- (repli sur le logo de volume /branding/sp-logo.png côté front).
CREATE TABLE sp_branding (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    logo_icone_id UUID REFERENCES sp_icone(id) ON DELETE SET NULL
);

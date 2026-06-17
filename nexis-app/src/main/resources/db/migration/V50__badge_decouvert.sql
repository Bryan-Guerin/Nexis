-- Mécanisme de découverte : le porteur d'un badge ne le voit pas tant qu'il
-- ne l'a pas découvert (clic). La notif lui dit qu'il a un nouveau badge sans
-- dévoiler lequel. Les autres membres voient les badges normalement (publics).
ALTER TABLE sp_membre_badge
    ADD COLUMN decouvert BOOLEAN NOT NULL DEFAULT FALSE;
-- Les badges déjà attribués (s'il y en a) restent non-découverts par défaut →
-- le porteur les "découvrira" la prochaine fois qu'il consulte sa fiche.

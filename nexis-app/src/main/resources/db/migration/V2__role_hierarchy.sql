-- Suppression de l'ancienne table ref_user_role (rôle en chaîne simple)
DROP TABLE IF EXISTS ref_user_role;

-- Hiérarchie de rôles auto-référencée
CREATE TABLE ref_role (
    id        UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code      VARCHAR(30)  NOT NULL UNIQUE,
    label     VARCHAR(100) NOT NULL,
    parent_id UUID         REFERENCES ref_role(id) ON DELETE RESTRICT
);

CREATE INDEX idx_ref_role_code ON ref_role(code);

-- Insertion de la hiérarchie : racine en premier, puis enfants par sous-sélection
INSERT INTO ref_role (code, label, parent_id) VALUES ('ROLE_SYSTEM', 'Système', NULL);

INSERT INTO ref_role (code, label, parent_id)
    SELECT 'ROLE_ADMIN_GN', 'Admin Gendarmerie', id FROM ref_role WHERE code = 'ROLE_SYSTEM';

INSERT INTO ref_role (code, label, parent_id)
    SELECT 'ROLE_ADMIN_SP', 'Admin Sapeurs-Pompiers', id FROM ref_role WHERE code = 'ROLE_SYSTEM';

INSERT INTO ref_role (code, label, parent_id)
    SELECT 'ROLE_GN', 'Gendarmerie', id FROM ref_role WHERE code = 'ROLE_ADMIN_GN';

INSERT INTO ref_role (code, label, parent_id)
    SELECT 'ROLE_SP', 'Sapeurs-Pompiers', id FROM ref_role WHERE code = 'ROLE_ADMIN_SP';

-- Nouvelle table de jonction ref_user_role (M:N avec UUID)
CREATE TABLE ref_user_role (
    user_id UUID NOT NULL REFERENCES ref_user(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES ref_role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

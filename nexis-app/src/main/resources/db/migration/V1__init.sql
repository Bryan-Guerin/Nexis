-- Utilisateurs de la plateforme Nexis
CREATE TABLE ref_user (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    username     VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(72),                     -- nullable : Steam-only accounts
    steam_id     BIGINT      UNIQUE,               -- Steam ID 64-bit (futur)
    enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Rôles des utilisateurs (ROLE_GN, ROLE_SP, ROLE_ADMIN, ...)
CREATE TABLE ref_user_role (
    user_id UUID        NOT NULL REFERENCES ref_user(id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE INDEX idx_ref_user_username ON ref_user(username);
CREATE INDEX idx_ref_user_steam_id ON ref_user(steam_id);

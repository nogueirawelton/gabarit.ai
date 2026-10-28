-- Preferências de estudo do usuário. Nome, e-mail e senha ficam no Keycloak (dono desses dados);
-- aqui só o que é do domínio do gabarit.
CREATE TABLE user_profile (
    -- "sub" do token do Keycloak: estável (o e-mail pode mudar)
    user_id    TEXT        PRIMARY KEY,
    -- estados de interesse (sigla), vazio = todos
    ufs        TEXT[]      NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Cargos de interesse, do catálogo da fonte. FK: só dá para seguir cargo que existe.
CREATE TABLE user_profile_position (
    user_id             TEXT   NOT NULL REFERENCES user_profile (user_id) ON DELETE CASCADE,
    catalog_position_id BIGINT NOT NULL REFERENCES position_catalog (id),
    PRIMARY KEY (user_id, catalog_position_id)
);

-- "quem segue o cargo X" (notificar quando abrir concurso)
CREATE INDEX user_profile_position_catalog_position_id_idx ON user_profile_position (catalog_position_id);

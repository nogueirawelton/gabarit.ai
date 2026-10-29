-- Aviso de concurso novo que bate com o perfil do usuário (sininho no app + resumo diário por e-mail).
-- Criado na mesma transação que cria o concurso; um por usuário e concurso.
CREATE TABLE notification (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    TEXT        NOT NULL REFERENCES user_profile (user_id) ON DELETE CASCADE,
    exam_id    BIGINT      NOT NULL REFERENCES exam (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    -- NULL = não lido (contador do sininho)
    read_at    TIMESTAMPTZ,
    -- NULL = ainda não foi em nenhum resumo por e-mail
    emailed_at TIMESTAMPTZ,

    CONSTRAINT notification_user_id_exam_id_key UNIQUE (user_id, exam_id)
);

-- lista do sininho (mais novos primeiro) e contagem de não lidos
CREATE INDEX notification_user_id_created_at_idx ON notification (user_id, created_at DESC);
-- resumo diário: só os que faltam enviar
CREATE INDEX notification_pending_email_idx ON notification (user_id) WHERE emailed_at IS NULL;

-- Contato para o resumo diário. E-mail e nome são do Keycloak; aqui fica a cópia do último token
-- visto (atualizada quando o usuário abre o app), e só e-mail confirmado recebe resumo.
ALTER TABLE user_profile
    ADD COLUMN email          TEXT,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN given_name     TEXT,
    -- o usuário pode desligar o resumo por e-mail (o sininho continua)
    ADD COLUMN email_digest   BOOLEAN NOT NULL DEFAULT TRUE;

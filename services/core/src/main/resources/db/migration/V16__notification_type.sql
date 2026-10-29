-- O sininho passa a ter mais de um tipo de aviso:
--   NEW_EXAM       concurso novo que bate com o perfil (o único até aqui; vai no resumo por e-mail)
--   EDITAL_READ    a leitura do edital de um concurso da carteira ficou pronta
--   EDITAL_FAILED  a leitura do edital falhou
-- Aviso de edital aponta para o concurso da carteira e guarda o título dele no momento do aviso
-- (a lista do sininho não depende do pacote study). Concurso removido da carteira leva os avisos.
ALTER TABLE notification
    ADD COLUMN type            TEXT NOT NULL DEFAULT 'NEW_EXAM',
    ADD COLUMN study_target_id UUID REFERENCES study_target (id) ON DELETE CASCADE,
    ADD COLUMN title           TEXT,
    ALTER COLUMN exam_id DROP NOT NULL;
ALTER TABLE notification ALTER COLUMN type DROP DEFAULT;

ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (
    (type = 'NEW_EXAM' AND exam_id IS NOT NULL)
    OR (type IN ('EDITAL_READ', 'EDITAL_FAILED') AND study_target_id IS NOT NULL AND title IS NOT NULL)
);

-- resumo diário por e-mail: só concurso novo (aviso de edital fica no sininho)
DROP INDEX notification_pending_email_idx;
CREATE INDEX notification_pending_email_idx ON notification (user_id) WHERE emailed_at IS NULL AND type = 'NEW_EXAM';
CREATE INDEX notification_study_target_id_idx ON notification (study_target_id);

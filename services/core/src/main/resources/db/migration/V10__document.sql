-- Arquivo enviado (PDF), guardado uma vez por conteúdo: o mesmo edital enviado por duas pessoas é
-- um blob só (e, depois, uma leitura só da LLM). Ninguém acessa o blob direto: o acesso é pelo
-- study_document de cada um.
CREATE TABLE document_blob (
    sha256      TEXT        PRIMARY KEY,
    storage_key TEXT        NOT NULL,
    size_bytes  BIGINT      NOT NULL,
    pages       INTEGER     NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

-- Arquivo de alguém num concurso da carteira: edital, prova, gabarito (ou prova com o gabarito junto).
CREATE TABLE study_document (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          TEXT        NOT NULL,
    study_target_id  BIGINT      NOT NULL REFERENCES study_target (id) ON DELETE CASCADE,
    kind             TEXT        NOT NULL,
    -- tipo/cor da prova ("Tipo 1", "Branca"): o gabarito costuma ter uma coluna por tipo
    variant          TEXT,
    original_name    TEXT        NOT NULL,
    -- UPLOADING (URL entregue, esperando o envio) → READY (conferido) ou REJECTED (não é PDF válido...)
    status           TEXT        NOT NULL,
    rejection_reason TEXT,
    -- nulos até o envio ser conferido (tamanho e páginas copiados do blob, para a lista)
    blob_sha256      TEXT        REFERENCES document_blob (sha256),
    size_bytes       BIGINT,
    pages            INTEGER,
    created_at       TIMESTAMPTZ NOT NULL,

    CONSTRAINT study_document_kind_check CHECK (kind IN ('EDITAL', 'PROVA', 'GABARITO', 'PROVA_COM_GABARITO')),
    CONSTRAINT study_document_status_check CHECK (status IN ('UPLOADING', 'READY', 'REJECTED'))
);

CREATE INDEX study_document_study_target_id_idx ON study_document (study_target_id, created_at);
-- limite de envios por dia por usuário
CREATE INDEX study_document_user_id_created_at_idx ON study_document (user_id, created_at);

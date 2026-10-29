-- Leituras da LLM (worker extraction), uma por (tarefa, entradas, versão do prompt): é também o
-- cache. O mesmo edital pedido por duas pessoas é lido uma vez só; prompt novo (versão nova)
-- lê de novo, e o resultado antigo fica para comparar.
CREATE TABLE extraction (
    id                          UUID        PRIMARY KEY,
    task                        TEXT        NOT NULL,
    -- SHA-256 do JSON canônico das entradas (hashes dos PDFs, na ordem, + parâmetros)
    inputs_hash                 TEXT        NOT NULL,
    prompt_version              TEXT        NOT NULL,
    -- as entradas em si, para reprocessar e conferir
    inputs                      JSONB       NOT NULL,
    -- PENDING (pedido enviado) → DONE ou FAILED; FAILED com retryable volta a PENDING no próximo pedido
    status                      TEXT        NOT NULL,
    result                      JSONB,
    failure_reason              TEXT,
    retryable                   BOOLEAN,
    -- modelo e tokens gastos (custo calculado a partir deles); nulos até concluir, e sem modelo
    -- quando a tarefa não usa LLM
    model                       TEXT,
    input_tokens                INTEGER,
    output_tokens               INTEGER,
    cache_read_input_tokens     INTEGER,
    cache_creation_input_tokens INTEGER,
    created_at                  TIMESTAMPTZ NOT NULL,
    -- último envio ao worker (o primeiro ou uma nova tentativa depois de falha)
    requested_at                TIMESTAMPTZ NOT NULL,
    completed_at                TIMESTAMPTZ,

    CONSTRAINT extraction_task_inputs_hash_prompt_version_key UNIQUE (task, inputs_hash, prompt_version),
    CONSTRAINT extraction_status_check CHECK (status IN ('PENDING', 'DONE', 'FAILED'))
);

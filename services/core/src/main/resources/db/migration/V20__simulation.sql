-- Simulado: uma prova relacionada feita como prova, com tempo opcional e correção no servidor.
--
-- question: as questões de uma leitura EXAM_PAPER, copiadas do resultado na primeira vez que alguém
-- faz simulado daquela prova. São do conteúdo (compartilhadas por todos que leram a mesma prova),
-- não do usuário; o simulado e as respostas apontam para elas por id.
CREATE TABLE question (
    id            UUID PRIMARY KEY,
    extraction_id UUID    NOT NULL REFERENCES extraction (id) ON DELETE CASCADE,
    -- ordem na prova (o número impresso pode repetir entre partes)
    ordinal       INTEGER NOT NULL,
    number        INTEGER NOT NULL,
    subject       TEXT,
    kind          TEXT    NOT NULL CHECK (kind IN ('MULTIPLE_CHOICE', 'TRUE_FALSE', 'ESSAY')),
    -- texto-base compartilhado (copiado em cada questão que o usa)
    base_text     TEXT,
    statement     TEXT    NOT NULL,
    -- [{"letter": "A", "text": "..."}]
    options       JSONB   NOT NULL,
    answer        TEXT,
    annulled      BOOLEAN NOT NULL,
    has_image     BOOLEAN NOT NULL,
    page          INTEGER,
    UNIQUE (extraction_id, ordinal)
);

CREATE TABLE simulation (
    id              UUID PRIMARY KEY,
    user_id         TEXT        NOT NULL,
    study_target_id UUID        NOT NULL REFERENCES study_target (id) ON DELETE CASCADE,
    -- a prova relacionada some: o simulado fica (as questões são da leitura)
    paper_id        UUID REFERENCES study_paper (id) ON DELETE SET NULL,
    extraction_id   UUID        NOT NULL REFERENCES extraction (id),
    -- "Prova de 2019 · Tipo 1", como era quando começou
    title           TEXT        NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    -- sem limite de tempo: nulo
    deadline        TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ
);
CREATE INDEX simulation_study_target_id_idx ON simulation (study_target_id, started_at DESC);

CREATE TABLE simulation_answer (
    id            UUID PRIMARY KEY,
    simulation_id UUID        NOT NULL REFERENCES simulation (id) ON DELETE CASCADE,
    question_id   UUID        NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    answer        TEXT        NOT NULL,
    answered_at   TIMESTAMPTZ NOT NULL,
    UNIQUE (simulation_id, question_id)
);

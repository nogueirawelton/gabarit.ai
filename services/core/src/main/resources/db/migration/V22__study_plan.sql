-- Plano de estudos de um concurso da carteira: até a data da prova, com as horas por dia da semana
-- que a pessoa tem. As sessões são calculadas no core (sem LLM): prioridade da disciplina = peso na
-- prova × quanto cai nas provas relacionadas × quanto a pessoa erra nos simulados.
CREATE TABLE study_plan (
    id              UUID PRIMARY KEY,
    user_id         TEXT        NOT NULL,
    study_target_id UUID        NOT NULL UNIQUE REFERENCES study_target (id) ON DELETE CASCADE,
    -- a prova (o plano vai até a véspera)
    end_date        DATE        NOT NULL,
    -- minutos por dia da semana, segunda primeiro
    daily_minutes   INTEGER[]   NOT NULL CHECK (cardinality(daily_minutes) = 7),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

-- Uma sessão do plano: estudar tópicos de uma disciplina, revisar ou fazer simulado. Refazer o plano
-- apaga as não feitas e recalcula; as feitas ficam.
CREATE TABLE plan_session (
    id              UUID PRIMARY KEY,
    plan_id         UUID        NOT NULL REFERENCES study_plan (id) ON DELETE CASCADE,
    date            DATE        NOT NULL,
    -- ordem no dia
    position        INTEGER     NOT NULL,
    kind            TEXT        NOT NULL CHECK (kind IN ('STUDY', 'REVIEW', 'SIMULATION')),
    -- disciplina do conteúdo do cargo (ordem e nome); nula na revisão da semana e no simulado
    subject_ordinal INTEGER,
    subject         TEXT,
    -- textos dos tópicos, como o conteúdo escreve
    topics          JSONB       NOT NULL,
    minutes         INTEGER     NOT NULL CHECK (minutes > 0),
    done_at         TIMESTAMPTZ
);
CREATE INDEX plan_session_plan_id_date_idx ON plan_session (plan_id, date, position);

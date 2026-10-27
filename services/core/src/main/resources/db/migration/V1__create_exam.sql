-- Concursos coletados pelas fontes (evento exam.discovered). Identidade: (source, external_id).
CREATE TABLE exam (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source             TEXT        NOT NULL,
    external_id        TEXT        NOT NULL,
    title              TEXT        NOT NULL,
    url                TEXT        NOT NULL,
    -- NULL em concursos nacionais
    uf                 TEXT,
    region             TEXT,
    education_level    TEXT,
    -- texto cru da fonte, ex.: "454 vagas até R$ 8.628,87"
    openings           TEXT,
    registration_start DATE,
    registration_end   DATE,
    -- situação como a fonte escreve, ex.: "Suspenso", "Prorrogado"
    registration_note  TEXT,
    -- occurred_at do último evento aplicado: evento mais antigo que isso é ignorado
    source_updated_at  TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,

    CONSTRAINT exam_source_external_id_key UNIQUE (source, external_id),
    CONSTRAINT exam_registration_period_check
        CHECK (registration_start IS NULL OR registration_end IS NULL OR registration_end >= registration_start)
);

-- Cargos do concurso. Nome cru da fonte, único dentro do concurso.
-- Cargo que some do evento fica inativo (não é apagado): planos de estudo vão referenciar cargo.
CREATE TABLE exam_position (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exam_id    BIGINT      NOT NULL REFERENCES exam (id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    active     BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    -- também serve de índice para buscar os cargos de um concurso (exam_id é a primeira coluna)
    CONSTRAINT exam_position_exam_id_name_key UNIQUE (exam_id, name)
);

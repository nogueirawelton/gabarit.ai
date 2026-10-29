-- Carteira: concursos que o usuário estuda. Pode ser um concurso da lista (exam_id) ou um passado,
-- cadastrado à mão (exam_id nulo): o edital enviado depois preenche órgão, banca, ano, cargo e data.
CREATE TABLE study_target (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- "sub" do Keycloak; sem FK para user_profile: dá para estudar sem ter salvo preferências
    user_id       TEXT        NOT NULL,
    -- concurso apagado da lista não apaga o estudo de ninguém
    exam_id       BIGINT      REFERENCES exam (id) ON DELETE SET NULL,
    title         TEXT        NOT NULL,
    organ         TEXT,
    board         TEXT,
    year          INTEGER,
    position_name TEXT,
    exam_date     DATE,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,

    -- o mesmo concurso da lista entra uma vez por usuário (concursos à mão, exam_id nulo, podem repetir)
    CONSTRAINT study_target_user_id_exam_id_key UNIQUE (user_id, exam_id),
    CONSTRAINT study_target_year_check CHECK (year IS NULL OR year BETWEEN 1950 AND 2100)
);

CREATE INDEX study_target_user_id_created_at_idx ON study_target (user_id, created_at DESC);

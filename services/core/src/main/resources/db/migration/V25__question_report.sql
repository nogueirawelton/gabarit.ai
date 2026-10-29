-- "Questão com problema": a pessoa reporta uma questão (gabarito errado, enunciado cortado...). Para
-- ela, a questão sai da nota e não volta na prática; com REPORTS_TO_HIDE reportes de pessoas
-- diferentes, sai da prática de todos. Os reportes viram dado para melhorar os prompts.
CREATE TABLE question_report (
    id            UUID PRIMARY KEY,
    question_id   UUID        NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    user_id       TEXT        NOT NULL,
    -- o simulado em que foi reportada (some se o simulado sumir; o reporte fica)
    simulation_id UUID REFERENCES simulation (id) ON DELETE SET NULL,
    reason        TEXT        NOT NULL CHECK (reason IN ('WRONG_ANSWER', 'BROKEN_STATEMENT', 'OUT_OF_SCOPE', 'OTHER')),
    comment       TEXT,
    created_at    TIMESTAMPTZ NOT NULL,
    UNIQUE (question_id, user_id)
);

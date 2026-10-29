-- Simulado de prática: questões escolhidas (as das provas relacionadas ligadas aos tópicos de uma
-- sessão do plano), de provas diferentes. Sem prova de origem: extraction_id nulo e a lista em
-- simulation_question, na ordem.
ALTER TABLE simulation ALTER COLUMN extraction_id DROP NOT NULL;

CREATE TABLE simulation_question (
    simulation_id UUID    NOT NULL REFERENCES simulation (id) ON DELETE CASCADE,
    question_id   UUID    NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    ordinal       INTEGER NOT NULL,
    PRIMARY KEY (simulation_id, question_id)
);

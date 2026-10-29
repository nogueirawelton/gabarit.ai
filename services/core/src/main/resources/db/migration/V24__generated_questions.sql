-- Questões inéditas geradas pela LLM para um tópico, no estilo da banca (TOPIC_QUESTIONS). Entram
-- na tabela question como as das provas (extraction_id = a leitura que gerou), marcadas como
-- geradas e com o comentário da resposta.
ALTER TABLE question
    ADD COLUMN generated   BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN explanation TEXT;

-- Pedido de geração de um tópico num concurso (um por tópico: pedir de novo substitui). A leitura
-- é do conteúdo (banca, formato e tópico): a mesma para todos que pedem o mesmo.
CREATE TABLE topic_generation (
    id              UUID PRIMARY KEY,
    user_id         TEXT        NOT NULL,
    study_target_id UUID        NOT NULL REFERENCES study_target (id) ON DELETE CASCADE,
    topic_id        UUID        NOT NULL REFERENCES syllabus_topic (id) ON DELETE CASCADE,
    extraction_id   UUID        NOT NULL REFERENCES extraction (id),
    requested_at    TIMESTAMPTZ NOT NULL,
    UNIQUE (study_target_id, topic_id)
);
CREATE INDEX topic_generation_extraction_id_idx ON topic_generation (extraction_id);

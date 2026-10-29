-- Questão → tópico do conteúdo programático.
--
-- syllabus_subject/syllabus_topic: o conteúdo de um cargo (leitura EDITAL_SYLLABUS), copiado do
-- resultado na primeira vez que alguém liga questões a ele. Como question, é do conteúdo
-- (compartilhado), não do usuário.
CREATE TABLE syllabus_subject (
    id            UUID PRIMARY KEY,
    extraction_id UUID    NOT NULL REFERENCES extraction (id) ON DELETE CASCADE,
    ordinal       INTEGER NOT NULL,
    name          TEXT    NOT NULL,
    group_name    TEXT,
    UNIQUE (extraction_id, ordinal)
);

CREATE TABLE syllabus_topic (
    id         UUID PRIMARY KEY,
    subject_id UUID    NOT NULL REFERENCES syllabus_subject (id) ON DELETE CASCADE,
    ordinal    INTEGER NOT NULL,
    text       TEXT    NOT NULL,
    UNIQUE (subject_id, ordinal)
);

-- uma questão pode estar ligada a tópicos de conteúdos diferentes (cargos diferentes)
CREATE TABLE question_topic (
    id          UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    topic_id    UUID NOT NULL REFERENCES syllabus_topic (id) ON DELETE CASCADE,
    UNIQUE (question_id, topic_id)
);
CREATE INDEX question_topic_topic_id_idx ON question_topic (topic_id);

-- pedido de ligação de uma prova relacionada (questões de exam_reading_id, tópicos de
-- syllabus_reading_id); a leitura QUESTION_TOPICS é extraction_id. Uma por prova: pedir de novo
-- (outra leitura da prova ou do conteúdo) substitui.
CREATE TABLE topic_classification (
    id                  UUID PRIMARY KEY,
    user_id             TEXT        NOT NULL,
    study_target_id     UUID        NOT NULL REFERENCES study_target (id) ON DELETE CASCADE,
    study_paper_id      UUID        NOT NULL UNIQUE REFERENCES study_paper (id) ON DELETE CASCADE,
    exam_reading_id     UUID        NOT NULL REFERENCES extraction (id),
    syllabus_reading_id UUID        NOT NULL REFERENCES extraction (id),
    extraction_id       UUID        NOT NULL REFERENCES extraction (id),
    requested_at        TIMESTAMPTZ NOT NULL
);
CREATE INDEX topic_classification_study_target_id_idx ON topic_classification (study_target_id);
CREATE INDEX topic_classification_extraction_id_idx ON topic_classification (extraction_id);

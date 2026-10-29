-- Prova anterior: uma prova de um concurso da carteira, com dois espaços (a prova e o gabarito
-- dela). Assim o par prova ↔ gabarito é explícito desde o envio, em vez de adivinhado depois.
CREATE TABLE study_paper (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          TEXT        NOT NULL,
    study_target_id  BIGINT      NOT NULL REFERENCES study_target (id) ON DELETE CASCADE,
    year             INTEGER,
    -- tipo/cor da prova ("Tipo 1", "Branca"): o gabarito costuma ter uma coluna por tipo
    variant          TEXT,
    -- o gabarito vem no mesmo PDF da prova (não há espaço separado para ele)
    answers_included BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL,

    CONSTRAINT study_paper_year_check CHECK (year IS NULL OR year BETWEEN 1950 AND 2100)
);

CREATE INDEX study_paper_study_target_id_idx ON study_paper (study_target_id, created_at);

ALTER TABLE study_document ADD COLUMN paper_id BIGINT REFERENCES study_paper (id) ON DELETE CASCADE;

-- Arquivos de prova/gabarito que já existiam (avulsos): cada um vira uma prova anterior própria.
-- PROVA_COM_GABARITO vira PROVA numa prova anterior com answers_included.
ALTER TABLE study_paper ADD COLUMN legacy_document_id BIGINT;
INSERT INTO study_paper (user_id, study_target_id, variant, answers_included, created_at, legacy_document_id)
SELECT user_id, study_target_id, variant, kind = 'PROVA_COM_GABARITO', created_at, id
FROM study_document
WHERE kind <> 'EDITAL';
UPDATE study_document d SET paper_id = p.id FROM study_paper p WHERE p.legacy_document_id = d.id;
ALTER TABLE study_paper DROP COLUMN legacy_document_id;

ALTER TABLE study_document DROP CONSTRAINT study_document_kind_check;
UPDATE study_document SET kind = 'PROVA' WHERE kind = 'PROVA_COM_GABARITO';
ALTER TABLE study_document
    ADD CONSTRAINT study_document_kind_check CHECK (kind IN ('EDITAL', 'PROVA', 'GABARITO')),
    -- edital é do concurso; prova e gabarito são de uma prova anterior
    ADD CONSTRAINT study_document_paper_check CHECK ((kind = 'EDITAL') = (paper_id IS NULL)),
    -- o tipo/cor agora é da prova anterior
    DROP COLUMN variant;

-- um espaço de prova e um de gabarito por prova anterior
CREATE UNIQUE INDEX study_document_paper_id_kind_key ON study_document (paper_id, kind) WHERE paper_id IS NOT NULL;

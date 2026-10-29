-- Leitura do conteúdo programático (EDITAL_SYLLABUS) do cargo escolhido no edital. Como a visão
-- geral, a leitura é do conteúdo (edital + cargo: a mesma para todos que escolheram esse cargo nesse
-- edital); aqui fica qual é a deste arquivo. Trocar de cargo pede outra.
ALTER TABLE study_document
    ADD COLUMN syllabus_extraction_id UUID REFERENCES extraction (id) ON DELETE SET NULL,
    ADD CONSTRAINT study_document_syllabus_check CHECK (syllabus_extraction_id IS NULL OR position_name IS NOT NULL);
CREATE INDEX study_document_syllabus_extraction_id_idx ON study_document (syllabus_extraction_id);

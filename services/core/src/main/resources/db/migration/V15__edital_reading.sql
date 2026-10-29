-- Leitura do edital do concurso-alvo pela LLM (EDITAL_OVERVIEW). A leitura é do conteúdo (a mesma
-- para todos que enviaram o mesmo edital); aqui fica só qual leitura é a deste arquivo.
-- Leitura apagada (não acontece hoje) só desfaz o vínculo.
ALTER TABLE study_document ADD COLUMN extraction_id UUID REFERENCES extraction (id) ON DELETE SET NULL;
CREATE INDEX study_document_extraction_id_idx ON study_document (extraction_id);

-- Leitura da prova relacionada pela LLM (EXAM_PAPER: prova + gabarito → questões). A leitura é do
-- conteúdo (a mesma para todos que enviaram a mesma prova, gabarito e tipo); aqui fica qual é a
-- desta prova relacionada. Trocar o PDF da prova ou do gabarito desfaz o vínculo.
ALTER TABLE study_paper ADD COLUMN extraction_id UUID REFERENCES extraction (id) ON DELETE SET NULL;
CREATE INDEX study_paper_extraction_id_idx ON study_paper (extraction_id);

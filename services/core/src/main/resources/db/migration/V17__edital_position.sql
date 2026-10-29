-- Cargo escolhido entre os da leitura do edital (EDITAL_OVERVIEW), como o edital escreve: é ele que
-- a leitura do conteúdo programático usa. Separado do cargo do concurso (study_target.position_name),
-- que num concurso da lista é o nome coletado pelo scraper. Fica no arquivo, não no concurso: some
-- junto com o edital, e outra leitura do edital (prompt novo) apaga a escolha.
ALTER TABLE study_document
    ADD COLUMN position_name TEXT,
    ADD COLUMN position_specialty TEXT,
    ADD CONSTRAINT study_document_position_check CHECK (
        position_name IS NULL AND position_specialty IS NULL
        OR position_name IS NOT NULL AND kind = 'EDITAL' AND paper_id IS NULL AND extraction_id IS NOT NULL
    );

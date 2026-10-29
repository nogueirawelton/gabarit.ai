-- Prova anterior ganha um terceiro espaço, opcional: o edital daquela prova. As questões de 2019
-- são classificadas pelo conteúdo do edital de 2019 (não pelo atual), e a comparação entre editais
-- mostra o que entrou e saiu. Edital sem prova anterior continua sendo o do concurso-alvo.
-- Um de cada por prova anterior: o índice único (paper_id, kind) já garante.
ALTER TABLE study_document DROP CONSTRAINT study_document_paper_check;
ALTER TABLE study_document
    ADD CONSTRAINT study_document_paper_check CHECK (kind = 'EDITAL' OR paper_id IS NOT NULL);

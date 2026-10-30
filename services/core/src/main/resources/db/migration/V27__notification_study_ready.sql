-- Mais avisos de leitura pronta no sininho (como os do edital, V16): conteúdo programático do cargo,
-- questões de uma prova relacionada e questões geradas (simulado inédito, prática por tópico).
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (
    (type = 'NEW_EXAM' AND exam_id IS NOT NULL)
    OR (type IN ('EDITAL_READ', 'EDITAL_FAILED', 'SYLLABUS_READ', 'SYLLABUS_FAILED', 'PAPER_READ', 'PAPER_FAILED',
                 'QUESTIONS_READY')
        AND study_target_id IS NOT NULL AND title IS NOT NULL)
);

-- Escolaridade como conjunto de níveis (FUNDAMENTAL, MEDIO, TECNICO, SUPERIOR), para filtrar.
-- O texto da fonte (education_level, ex.: "Fundamental / Médio / Superior") continua para exibição;
-- os níveis são extraídos dele pelo core a cada evento (EducationLevel.parse).
-- VARCHAR[] e não TEXT[]: é o tipo que o Hibernate usa nos parâmetros (ver V5).
ALTER TABLE exam ADD COLUMN education_levels VARCHAR[] NOT NULL DEFAULT '{}';

-- concursos que já estão no banco: mesma regra do EducationLevel.parse (sem acento, sem caixa)
UPDATE exam
SET education_levels = ARRAY_REMOVE(ARRAY[
        CASE WHEN education_level ILIKE '%fundamental%' THEN 'FUNDAMENTAL' END,
        CASE WHEN education_level ILIKE '%médio%' OR education_level ILIKE '%medio%' THEN 'MEDIO' END,
        CASE WHEN education_level ILIKE '%técnico%' OR education_level ILIKE '%tecnico%' THEN 'TECNICO' END,
        CASE WHEN education_level ILIKE '%superior%' THEN 'SUPERIOR' END
    ], NULL)::VARCHAR[]
WHERE education_level IS NOT NULL;

-- "concursos com vaga de nível médio": WHERE education_levels && ARRAY['MEDIO']::VARCHAR[]
CREATE INDEX exam_education_levels_idx ON exam USING GIN (education_levels);

-- Níveis de vaga que interessam ao usuário; vazio = todos (mesma regra das UFs).
ALTER TABLE user_profile ADD COLUMN education_levels VARCHAR[] NOT NULL DEFAULT '{}';

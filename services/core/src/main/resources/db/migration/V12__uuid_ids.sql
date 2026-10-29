-- Todos os ids passam de BIGINT sequencial para UUID. Id sequencial na URL deixa adivinhar recursos
-- (/estudos/5 → /estudos/6) e mostra o volume. Ids novos: UUID v7, gerado pela aplicação (ordenado
-- pelo tempo: não espalha inserções pelo índice como o aleatório). As linhas que já existem ganham
-- um UUID aleatório (gen_random_uuid): a ordem delas não importa mais.
--
-- Conversão em três passos, preservando os vínculos:
--   1. cada tabela ganha o id novo;
--   2. cada referência copia o id novo da linha que aponta;
--   3. troca das colunas: referências primeiro (as FKs saem junto com a coluna antiga), depois os
--      ids; e recriação de PK, FK, UNIQUE, índices e CHECK que usavam as colunas trocadas
--      (DROP COLUMN derruba tudo que usa a coluna).

-- 1. ids novos ---------------------------------------------------------------------------------
ALTER TABLE exam             ADD COLUMN new_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE exam_position    ADD COLUMN new_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE position_catalog ADD COLUMN new_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE notification     ADD COLUMN new_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE study_target     ADD COLUMN new_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE study_paper      ADD COLUMN new_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE study_document   ADD COLUMN new_id UUID NOT NULL DEFAULT gen_random_uuid();

-- 2. referências copiam o id novo ----------------------------------------------------------------
ALTER TABLE exam_position ADD COLUMN new_exam_id UUID;
UPDATE exam_position c SET new_exam_id = p.new_id FROM exam p WHERE p.id = c.exam_id;

ALTER TABLE notification ADD COLUMN new_exam_id UUID;
UPDATE notification c SET new_exam_id = p.new_id FROM exam p WHERE p.id = c.exam_id;

ALTER TABLE study_target ADD COLUMN new_exam_id UUID;
UPDATE study_target c SET new_exam_id = p.new_id FROM exam p WHERE p.id = c.exam_id;

ALTER TABLE user_profile_position ADD COLUMN new_catalog_position_id UUID;
UPDATE user_profile_position c SET new_catalog_position_id = p.new_id
FROM position_catalog p WHERE p.id = c.catalog_position_id;

ALTER TABLE study_paper ADD COLUMN new_study_target_id UUID;
UPDATE study_paper c SET new_study_target_id = p.new_id FROM study_target p WHERE p.id = c.study_target_id;

ALTER TABLE study_document ADD COLUMN new_study_target_id UUID;
UPDATE study_document c SET new_study_target_id = p.new_id FROM study_target p WHERE p.id = c.study_target_id;

ALTER TABLE study_document ADD COLUMN new_paper_id UUID;
UPDATE study_document c SET new_paper_id = p.new_id FROM study_paper p WHERE p.id = c.paper_id;

-- 3a. troca das referências ----------------------------------------------------------------------
ALTER TABLE exam_position DROP COLUMN exam_id;
ALTER TABLE exam_position RENAME COLUMN new_exam_id TO exam_id;
ALTER TABLE exam_position ALTER COLUMN exam_id SET NOT NULL;

ALTER TABLE notification DROP COLUMN exam_id;
ALTER TABLE notification RENAME COLUMN new_exam_id TO exam_id;
ALTER TABLE notification ALTER COLUMN exam_id SET NOT NULL;

ALTER TABLE study_target DROP COLUMN exam_id;
ALTER TABLE study_target RENAME COLUMN new_exam_id TO exam_id;

ALTER TABLE user_profile_position DROP COLUMN catalog_position_id;
ALTER TABLE user_profile_position RENAME COLUMN new_catalog_position_id TO catalog_position_id;
ALTER TABLE user_profile_position ALTER COLUMN catalog_position_id SET NOT NULL;

ALTER TABLE study_paper DROP COLUMN study_target_id;
ALTER TABLE study_paper RENAME COLUMN new_study_target_id TO study_target_id;
ALTER TABLE study_paper ALTER COLUMN study_target_id SET NOT NULL;

ALTER TABLE study_document DROP COLUMN study_target_id;
ALTER TABLE study_document RENAME COLUMN new_study_target_id TO study_target_id;
ALTER TABLE study_document ALTER COLUMN study_target_id SET NOT NULL;

ALTER TABLE study_document DROP COLUMN paper_id;
ALTER TABLE study_document RENAME COLUMN new_paper_id TO paper_id;

-- 3b. troca dos ids (ninguém mais aponta para as colunas antigas) -------------------------------
ALTER TABLE exam             DROP COLUMN id;
ALTER TABLE exam_position    DROP COLUMN id;
ALTER TABLE position_catalog DROP COLUMN id;
ALTER TABLE notification     DROP COLUMN id;
ALTER TABLE study_target     DROP COLUMN id;
ALTER TABLE study_paper      DROP COLUMN id;
ALTER TABLE study_document   DROP COLUMN id;

ALTER TABLE exam             RENAME COLUMN new_id TO id;
ALTER TABLE exam_position    RENAME COLUMN new_id TO id;
ALTER TABLE position_catalog RENAME COLUMN new_id TO id;
ALTER TABLE notification     RENAME COLUMN new_id TO id;
ALTER TABLE study_target     RENAME COLUMN new_id TO id;
ALTER TABLE study_paper      RENAME COLUMN new_id TO id;
ALTER TABLE study_document   RENAME COLUMN new_id TO id;

-- daqui para a frente quem gera o id é a aplicação (v7)
ALTER TABLE exam             ALTER COLUMN id DROP DEFAULT, ADD PRIMARY KEY (id);
ALTER TABLE exam_position    ALTER COLUMN id DROP DEFAULT, ADD PRIMARY KEY (id);
ALTER TABLE position_catalog ALTER COLUMN id DROP DEFAULT, ADD PRIMARY KEY (id);
ALTER TABLE notification     ALTER COLUMN id DROP DEFAULT, ADD PRIMARY KEY (id);
ALTER TABLE study_target     ALTER COLUMN id DROP DEFAULT, ADD PRIMARY KEY (id);
ALTER TABLE study_paper      ALTER COLUMN id DROP DEFAULT, ADD PRIMARY KEY (id);
ALTER TABLE study_document   ALTER COLUMN id DROP DEFAULT, ADD PRIMARY KEY (id);

-- 3c. constraints e índices que saíram junto com as colunas (mesmos nomes e regras de antes) ----
ALTER TABLE exam_position
    ADD CONSTRAINT exam_position_exam_id_fkey FOREIGN KEY (exam_id) REFERENCES exam (id) ON DELETE CASCADE,
    ADD CONSTRAINT exam_position_exam_id_name_key UNIQUE (exam_id, name);

ALTER TABLE user_profile_position
    ADD CONSTRAINT user_profile_position_pkey PRIMARY KEY (user_id, catalog_position_id),
    ADD CONSTRAINT user_profile_position_catalog_position_id_fkey
        FOREIGN KEY (catalog_position_id) REFERENCES position_catalog (id);
CREATE INDEX user_profile_position_catalog_position_id_idx ON user_profile_position (catalog_position_id);

ALTER TABLE notification
    ADD CONSTRAINT notification_exam_id_fkey FOREIGN KEY (exam_id) REFERENCES exam (id) ON DELETE CASCADE,
    ADD CONSTRAINT notification_user_id_exam_id_key UNIQUE (user_id, exam_id);

ALTER TABLE study_target
    ADD CONSTRAINT study_target_exam_id_fkey FOREIGN KEY (exam_id) REFERENCES exam (id) ON DELETE SET NULL,
    ADD CONSTRAINT study_target_user_id_exam_id_key UNIQUE (user_id, exam_id);

ALTER TABLE study_paper
    ADD CONSTRAINT study_paper_study_target_id_fkey
        FOREIGN KEY (study_target_id) REFERENCES study_target (id) ON DELETE CASCADE;
CREATE INDEX study_paper_study_target_id_idx ON study_paper (study_target_id, created_at);

ALTER TABLE study_document
    ADD CONSTRAINT study_document_study_target_id_fkey
        FOREIGN KEY (study_target_id) REFERENCES study_target (id) ON DELETE CASCADE,
    ADD CONSTRAINT study_document_paper_id_fkey
        FOREIGN KEY (paper_id) REFERENCES study_paper (id) ON DELETE CASCADE,
    ADD CONSTRAINT study_document_paper_check CHECK ((kind = 'EDITAL') = (paper_id IS NULL));
CREATE INDEX study_document_study_target_id_idx ON study_document (study_target_id, created_at);
CREATE UNIQUE INDEX study_document_paper_id_kind_key ON study_document (paper_id, kind) WHERE paper_id IS NOT NULL;

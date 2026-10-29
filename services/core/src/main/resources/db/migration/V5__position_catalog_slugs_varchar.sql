-- O Hibernate envia array de String como varchar[], e o Postgres não acha o operador
-- text[] && varchar[] (sem conversão implícita entre arrays). Com a coluna em varchar[],
-- "catalog_slugs && ?" funciona e continua usando o índice GIN (recriado pelo ALTER).
-- varchar sem tamanho = text no Postgres: mesmo armazenamento e desempenho.
ALTER TABLE exam_position ALTER COLUMN catalog_slugs TYPE VARCHAR[];
-- SQL à mão: tipe o array literal, ex.: WHERE catalog_slugs @> ARRAY['enfermeiro']::VARCHAR[]

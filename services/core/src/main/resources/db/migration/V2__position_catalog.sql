-- Catálogo de cargos da fonte (evento position_catalog.discovered). Ex.: "Enfermeiro".
-- Identidade: (source, slug). Cargo que some do catálogo não é apagado.
CREATE TABLE position_catalog (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source            TEXT        NOT NULL,
    slug              TEXT        NOT NULL,
    name              TEXT        NOT NULL,
    -- occurred_at do último evento aplicado: evento mais antigo que isso é ignorado
    source_updated_at TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,

    CONSTRAINT position_catalog_source_slug_key UNIQUE (source, slug)
);

-- Em quais cargos do catálogo (slug, mesma source do concurso) o nome cru apareceu.
-- Array e não FK: o catálogo e os concursos chegam por filas diferentes, sem ordem garantida.
ALTER TABLE exam_position ADD COLUMN catalog_slugs TEXT[] NOT NULL DEFAULT '{}';

-- "concursos com vagas para enfermeiro": WHERE catalog_slugs @> ARRAY['enfermeiro']
CREATE INDEX exam_position_catalog_slugs_idx ON exam_position USING GIN (catalog_slugs);

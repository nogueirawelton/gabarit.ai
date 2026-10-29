-- Custo das leituras pela LLM e quem pediu (a primeira pessoa: quem reaproveita a leitura de outra
-- não gasta nada).
--
-- cost_usd: calculado pelo core quando a leitura termina (ModelPricing). As leituras que já existem
-- recebem a mesma conta aqui (mesma tabela de preços; requested_by fica nulo: não se sabe quem pediu).
ALTER TABLE extraction
    ADD COLUMN requested_by TEXT,
    ADD COLUMN cost_usd     NUMERIC(12, 6);

-- trava diária por pessoa (pedidos novos nas últimas 24 h)
CREATE INDEX extraction_requested_by_created_at_idx ON extraction (requested_by, created_at);

-- US$ por milhão de tokens: entrada, saída, leitura de cache, gravação de cache (5 min)
CREATE TEMPORARY TABLE price (model TEXT, long_prompt BOOLEAN, input NUMERIC, output NUMERIC,
    cache_read NUMERIC, cache_write NUMERIC);
INSERT INTO price VALUES
    -- Haiku 5.5: 5× mais com prompt acima de 100 mil tokens
    ('claude-haiku-5-5', false, 0.10, 0.50, 0.01, 0.125),
    ('claude-haiku-5-5', true, 0.50, 2.50, 0.05, 0.625),
    ('claude-sonnet-5-5', false, 2.00, 10.00, 0.20, 2.50),
    ('claude-sonnet-5-5', true, 2.00, 10.00, 0.20, 2.50),
    ('claude-opus-5-5', false, 4.00, 20.00, 0.20, 5.00),
    ('claude-opus-5-5', true, 4.00, 20.00, 0.20, 5.00);

UPDATE extraction e
SET cost_usd = round((e.input_tokens * p.input + e.output_tokens * p.output
        + coalesce(e.cache_read_input_tokens, 0) * p.cache_read
        + coalesce(e.cache_creation_input_tokens, 0) * p.cache_write) / 1000000.0, 6)
FROM price p
WHERE e.status = 'DONE'
  AND e.input_tokens IS NOT NULL
  AND p.model = e.model
  AND p.long_prompt = (e.input_tokens + coalesce(e.cache_read_input_tokens, 0)
        + coalesce(e.cache_creation_input_tokens, 0) > 100000);

DROP TABLE price;

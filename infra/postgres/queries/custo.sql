-- Gasto com a LLM nos últimos 30 dias, em US$ (make custo). O custo é gravado em cada leitura quando
-- termina (extraction.cost_usd, tabela de preços em core/extraction/ModelPricing). Leitura
-- compartilhada conta uma vez, para quem pediu primeiro.
\pset footer off

\echo '== total (30 dias)'
SELECT round(sum(cost_usd), 4) AS usd, count(*) AS leituras,
       count(*) FILTER (WHERE status = 'FAILED') AS falhas
FROM extraction WHERE created_at > now() - interval '30 days';

\echo '== por dia, tarefa e modelo'
SELECT created_at::date AS dia, task AS tarefa, model AS modelo, count(*) AS leituras,
       round(sum(cost_usd), 4) AS usd, sum(input_tokens) AS entrada, sum(output_tokens) AS saida
FROM extraction WHERE created_at > now() - interval '30 days'
GROUP BY 1, 2, 3 ORDER BY 1 DESC, usd DESC NULLS LAST;

\echo '== por pessoa'
SELECT coalesce(requested_by, '(antes da V26)') AS pessoa, count(*) AS leituras, round(sum(cost_usd), 4) AS usd
FROM extraction WHERE created_at > now() - interval '30 days'
GROUP BY 1 ORDER BY usd DESC NULLS LAST;

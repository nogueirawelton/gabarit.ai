-- Busca sem acento: "tecnico" acha "Técnico". unaccent é extensão "trusted" (PG 13+): o dono do
-- banco (usuário core) cria sem superusuário, então vale para bancos que já existem.
-- Sem índice por enquanto: são centenas de concursos. Quando pesar, índice trigram (pg_trgm, também
-- trusted) numa função IMMUTABLE que embrulhe o unaccent (ele próprio é só STABLE).
CREATE EXTENSION IF NOT EXISTS unaccent;

# Postgres 16 + pgvector com os scripts de init (banco/usuário de cada serviço) dentro da imagem:
# produção não depende de arquivo do repositório montado. Os scripts só rodam com o volume vazio.
FROM pgvector/pgvector:0.8.0-pg16
COPY --chmod=755 init/ /docker-entrypoint-initdb.d/

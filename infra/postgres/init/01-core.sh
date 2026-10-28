#!/bin/bash
# Banco e usuário do serviço `core` (Java: todo o backend). Tabelas criadas pelas migrations do serviço.
# Só roda com o volume vazio: o entrypoint do Postgres ignora /docker-entrypoint-initdb.d
# quando os dados já existem. Mudou algo aqui? Aplique à mão ou recrie o volume (make reset).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v core_password="$CORE_DB_PASSWORD" <<-'EOSQL'
	CREATE ROLE core LOGIN PASSWORD :'core_password';
	CREATE DATABASE core OWNER core;
	\connect core
	-- extensão exige superusuário; schemas e tabelas ficam com o dono `core` (migrations do serviço)
	CREATE EXTENSION IF NOT EXISTS vector;
	REVOKE ALL ON SCHEMA public FROM PUBLIC;
	ALTER SCHEMA public OWNER TO core;
EOSQL

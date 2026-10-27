#!/bin/bash
# Banco e usuário do serviço `exam` (cada serviço é dono dos próprios dados).
# Só roda com o volume vazio: o entrypoint do Postgres ignora /docker-entrypoint-initdb.d
# quando os dados já existem. Mudou algo aqui? Aplique à mão ou recrie o volume (make reset).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v exam_password="$EXAM_DB_PASSWORD" <<-'EOSQL'
	CREATE ROLE exam LOGIN PASSWORD :'exam_password';
	CREATE DATABASE exam OWNER exam;
	\connect exam
	-- extensão exige superusuário; as tabelas ficam com o dono `exam` (migrations do serviço)
	CREATE EXTENSION IF NOT EXISTS vector;
	REVOKE ALL ON SCHEMA public FROM PUBLIC;
	ALTER SCHEMA public OWNER TO exam;
EOSQL

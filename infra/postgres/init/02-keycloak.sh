#!/bin/bash
# Banco e usuário do Keycloak (autenticação). Separado do `core`: o Keycloak é dono das próprias tabelas.
# Só roda com o volume vazio: o entrypoint do Postgres ignora /docker-entrypoint-initdb.d
# quando os dados já existem. Mudou algo aqui? Aplique à mão ou recrie o volume (make reset).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v keycloak_password="$KEYCLOAK_DB_PASSWORD" <<-'EOSQL'
	CREATE ROLE keycloak LOGIN PASSWORD :'keycloak_password';
	CREATE DATABASE keycloak OWNER keycloak;
	\connect keycloak
	REVOKE ALL ON SCHEMA public FROM PUBLIC;
	ALTER SCHEMA public OWNER TO keycloak;
EOSQL

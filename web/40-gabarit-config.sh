#!/bin/sh
# Roda no boot do Nginx (docker-entrypoint.d): gera o config.js com as variáveis do container.
set -eu

: "${KEYCLOAK_URL:?defina KEYCLOAK_URL}"
: "${API_URL:?defina API_URL}"
export KEYCLOAK_REALM="${KEYCLOAK_REALM:-gabaritai}"
export KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-web}"

envsubst '${KEYCLOAK_URL} ${KEYCLOAK_REALM} ${KEYCLOAK_CLIENT_ID} ${API_URL}' \
  < /etc/gabarit/config.js.template > /usr/share/nginx/html/config.js
echo "config.js gerado (keycloak=${KEYCLOAK_URL}, api=${API_URL})"

#!/bin/sh
# Importa a topologia compartilhada (definitions.json) pela API do management.
# Roda uma vez a cada `docker compose up`, depois que o RabbitMQ fica saudável.
# Import é merge: só cria/confirma o que está no arquivo, não apaga nada.
# Fila declarada aqui com argumentos diferentes dos que já existem no broker faz o import falhar.
set -eu

rabbitmqadmin \
  --host="${RABBITMQ_HOST:-rabbitmq}" \
  --username="$RABBITMQ_USER" \
  --password="$RABBITMQ_PASSWORD" \
  import /definitions.json

echo "topologia importada"

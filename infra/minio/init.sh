#!/bin/sh
# Buckets, policies e usuários de cada serviço no MinIO, pela API admin (mc) com o usuário root.
# Roda uma vez a cada `docker compose up`, depois que o MinIO fica saudável. Idempotente:
# bucket existente é mantido, policy é sobrescrita pelo arquivo, senha do usuário volta à do ambiente.
set -eu

# "saudável" (health/live) não garante que a API de administração já responde ("Server not
# initialized yet"): tenta por até ~30 s antes de desistir
tries=0
until mc alias set gabarit "${MINIO_URL:-http://minio:9000}" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; do
  tries=$((tries + 1))
  if [ "$tries" -ge 15 ]; then
    echo "MinIO não respondeu à API de administração" >&2
    exit 1
  fi
  sleep 2
done

# fotos de perfil: privado (sem acesso anônimo); o navegador lê por URL pré-assinada pelo core
mc mb --ignore-existing gabarit/avatars
mc anonymous set none gabarit/avatars

# edital, provas e gabaritos enviados pelos usuários: privado. O navegador envia direto para
# uploads/ (URL pré-assinada pelo core); o core confere o PDF e move para blobs/<sha256>.pdf.
# Upload abandonado (nunca confirmado) some sozinho em 1 dia.
mc mb --ignore-existing gabarit/documents
mc anonymous set none gabarit/documents
# (a imagem não tem grep: o "já existe?" é um case do shell)
rules=$(mc ilm rule ls gabarit/documents --json 2>/dev/null || true)
case "$rules" in
  *'"Prefix":"uploads/"'*) ;;
  *) mc ilm rule add gabarit/documents --prefix "uploads/" --expire-days 1 >/dev/null ;;
esac

# core: lê e escreve só nos próprios buckets
mc admin policy create gabarit core /policies/core.json
mc admin user add gabarit core "$CORE_MINIO_PASSWORD"
mc admin policy attach gabarit core --user core

# extraction: só lê os PDFs já conferidos (documents/blobs/), nada mais
mc admin policy create gabarit extraction /policies/extraction.json
mc admin user add gabarit extraction "$EXTRACTION_MINIO_PASSWORD"
mc admin policy attach gabarit extraction --user extraction

echo "buckets e acessos prontos"

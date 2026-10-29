# MinIO (armazenamento de arquivos, API S3) e o one-shot que prepara buckets e acessos.
# Imagem do Coolify: a MinIO parou de publicar imagens da edição community; esta é compilada
# do código-fonte oficial, com tag fixa. Arquivos dentro da imagem: produção não depende de
# arquivo do repositório montado.
ARG MINIO_IMAGE=coollabsio/minio:RELEASE.2025-10-15T17-29-55Z

# ---------- servidor ----------
# /data já com o dono certo: volume nomeado novo herda a permissão do diretório da imagem
FROM ${MINIO_IMAGE} AS server
RUN mkdir -p /data && chown 10001:10001 /data
USER 10001:10001
ENV HOME=/tmp
CMD ["server", "/data", "--address", ":9000", "--console-address", ":9001"]

# ---------- init (one-shot) ----------
FROM ${MINIO_IMAGE} AS init
COPY --chmod=755 init.sh /init.sh
COPY policies/ /policies/
USER 10001:10001
# o mc grava o alias em disco: diretório gravável sem root
ENV MC_CONFIG_DIR=/tmp/.mc
ENTRYPOINT ["/init.sh"]

# syntax=docker/dockerfile:1

# Agendador: supercronic (cron feito para container: log no stdout, sem root, SIGTERM limpo)
# + script de publish só com stdlib. Estágio único: não há código de app nem ferramentas de dev.
ARG PYTHON_IMAGE=python:3.13-slim
ARG TARGETARCH

# ---------- supercronic, com checksum conferido no download ----------
FROM scratch AS supercronic-amd64
ADD --checksum=sha256:a53ae236602c7338aba3fbaff40bda6300eae3b9fedb8261eb06cfe3724430c1 --chmod=755 \
    https://github.com/aptible/supercronic/releases/download/v0.2.49/supercronic-linux-amd64 /supercronic

FROM scratch AS supercronic-arm64
ADD --checksum=sha256:02aa0cb229ba09050cba6638059dadb9eedc2276632ea43d6a57a2f8c1629dd5 --chmod=755 \
    https://github.com/aptible/supercronic/releases/download/v0.2.49/supercronic-linux-arm64 /supercronic

FROM supercronic-${TARGETARCH} AS supercronic

# ---------- runtime ----------
FROM ${PYTHON_IMAGE}
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app
COPY --from=supercronic /supercronic /usr/local/bin/supercronic
COPY --chmod=755 publish.py /usr/local/bin/publish
COPY crontab /etc/crontab
USER app
CMD ["supercronic", "-passthrough-logs", "/etc/crontab"]

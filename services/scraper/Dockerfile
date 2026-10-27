# syntax=docker/dockerfile:1

ARG PYTHON_IMAGE=python:3.13-slim
# Precisa bater com o playwright do requirements.lock (o build do builder confere)
ARG PLAYWRIGHT_VERSION=1.63.0

# ==========================================
# BASE: configuração comum a todos os estágios
# ==========================================
FROM ${PYTHON_IMAGE} AS base
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    VIRTUAL_ENV=/opt/venv \
    PATH="/opt/venv/bin:$PATH"
WORKDIR /app

# ==========================================
# BUILDER: instala as dependências de produção num venv isolado
# ==========================================
FROM base AS builder
ARG PLAYWRIGHT_VERSION
RUN python -m venv /opt/venv
COPY requirements.lock .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-deps -r requirements.lock
RUN python -c "import importlib.metadata as m, sys; v = m.version('playwright'); \
sys.exit(None if v == '${PLAYWRIGHT_VERSION}' else f'playwright {v} no requirements.lock != PLAYWRIGHT_VERSION ${PLAYWRIGHT_VERSION} no Dockerfile')"

# ==========================================
# RUNTIME: base + Chromium headless do Playwright e as libs de sistema dele
# Instalado num venv descartável: mudar as dependências não baixa o navegador de novo
# ==========================================
FROM base AS runtime
ARG PLAYWRIGHT_VERSION
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    --mount=type=cache,target=/root/.cache/pip \
    rm -f /etc/apt/apt.conf.d/docker-clean \
    && python -m venv /tmp/pw \
    && /tmp/pw/bin/pip install "playwright==${PLAYWRIGHT_VERSION}" \
    && /tmp/pw/bin/playwright install --with-deps --only-shell chromium \
    && rm -rf /tmp/pw

# ==========================================
# DEV: deps de prod + ferramentas de dev; código entra via bind mount
# ==========================================
FROM runtime AS dev
COPY --from=builder /opt/venv /opt/venv
COPY requirements-dev.lock .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-deps -r requirements-dev.lock
CMD ["watchfiles", "python -m src.main", "src"]

# ==========================================
# PROD: só o venv e o código, sem pip cache, sem ferramentas de dev, sem root
# ==========================================
FROM runtime AS prod
# home própria: o Chromium precisa de um diretório gravável para perfil e cache
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --create-home app
COPY --from=builder /opt/venv /opt/venv
# código fica com dono root: o processo roda como 'app' e não consegue alterá-lo
COPY src ./src
USER app
CMD ["python", "-m", "src.main"]

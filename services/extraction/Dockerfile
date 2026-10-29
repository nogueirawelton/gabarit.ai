# syntax=docker/dockerfile:1

ARG PYTHON_IMAGE=python:3.13-slim

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
RUN python -m venv /opt/venv
COPY requirements.lock .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-deps -r requirements.lock

# ==========================================
# DEV: deps de prod + ferramentas de dev; código entra via bind mount
# ==========================================
FROM base AS dev
COPY --from=builder /opt/venv /opt/venv
COPY requirements-dev.lock .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-deps -r requirements-dev.lock
CMD ["watchfiles", "python -m src.main", "src"]

# ==========================================
# PROD: só o venv e o código, sem pip cache, sem ferramentas de dev, sem root
# ==========================================
FROM base AS prod
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app
COPY --from=builder /opt/venv /opt/venv
# código fica com dono root: o processo roda como 'app' e não consegue alterá-lo
COPY src ./src
USER app
CMD ["python", "-m", "src.main"]

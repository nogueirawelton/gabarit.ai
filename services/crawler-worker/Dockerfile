# ==========================================
# 1. ESTÁGIO BASE
# ==========================================
FROM python:3.12-slim AS base
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app/src
WORKDIR /app
# libpq5 é necessária para o asyncpg (PostgreSQL) em runtime
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq5 \
    && rm -rf /var/lib/apt/lists/*

# ==========================================
# 2. ESTÁGIO BUILDER    
# ==========================================
FROM base AS builder
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    python3-dev \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip wheel --no-cache-dir --wheel-dir /app/wheels -r requirements.txt

# ==========================================
# 3. ESTÁGIO DE DEV (Foco em Hot Reload/Bind Mount)
# ==========================================
FROM base AS dev
COPY --from=builder /app/wheels /wheels
RUN pip install --no-cache-dir --no-index --find-links=/wheels /wheels/*.whl \
    && rm -rf /wheels

RUN pip install watchfiles
CMD ["python", "src/main.py"]

# ==========================================
# 4. ESTÁGIO DE PROD
# ==========================================
FROM base AS prod
COPY --from=builder /app/wheels /wheels
RUN pip install --no-cache-dir --no-index --find-links=/wheels /wheels/*.whl \
    && rm -rf /wheels

COPY ./src ./src
RUN useradd -m appuser && chown -R appuser /app
USER appuser

CMD ["python", "-m", "src.main"]
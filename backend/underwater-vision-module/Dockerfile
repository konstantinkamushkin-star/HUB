# Сборка: из каталога backend — docker build -f underwater-vision-module/Dockerfile underwater-vision-module
# Зависимости строго из uv.lock (пины pyproject, в т.ч. opencv-python-headless==4.10.0.84).
# Полная переустановка: docker compose build --no-cache uvm

FROM python:3.11-slim-bookworm

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    libglib2.0-0 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
ENV PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    PYTHONPATH=/app/src

COPY uv.lock pyproject.toml README.md ./
COPY configs ./configs
COPY src ./src

# Каждый билд — новый .venv внутри слоя (без «наслоения» старого site-packages).
RUN rm -rf .venv && \
    uv venv .venv --python 3.11 && \
    UV_NO_CACHE=1 UV_PROJECT_ENVIRONMENT=/app/.venv uv sync --frozen --no-dev

ENV PATH="/app/.venv/bin:$PATH"

EXPOSE 8010
HEALTHCHECK --interval=30s --timeout=8s --start-period=300s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:8010/docs" >/dev/null || exit 1

CMD ["sh", "-c", "exec uvicorn uvm.api.app:app --host 0.0.0.0 --port ${PORT:-8010}"]

#!/usr/bin/env bash
# Main Responsibility: One-shot local clone setup (env, Compose, health wait, frontend deps).
# Run from the repository root:  ./scripts/setup.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

if [[ ! -f "${REPO_ROOT}/docker-compose.yml" ]]; then
    echo "docker-compose.yml not found. Run this script from the repository root (or via ./scripts/setup.sh)." >&2
    exit 1
fi

ENV_FILE="${REPO_ROOT}/.env"
ENV_EXAMPLE="${REPO_ROOT}/.env.example"

# Create a local .env only when missing so existing secrets are never overwritten.
if [[ ! -f "${ENV_FILE}" ]]; then
    if [[ ! -f "${ENV_EXAMPLE}" ]]; then
        echo ".env.example is missing; cannot create .env." >&2
        exit 1
    fi
    cp "${ENV_EXAMPLE}" "${ENV_FILE}"
    echo "Created .env from .env.example"
else
    echo ".env already exists - leaving it unchanged"
fi

# Warn when Groq is unset; do not invent a key (processing fails until the user fills it).
GROQ_KEY="$(
    grep -E '^\s*GROQ_API_KEY\s*=' "${ENV_FILE}" | tail -n 1 | cut -d '=' -f 2- | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//" || true
)"
if [[ -z "${GROQ_KEY}" ]]; then
    echo
    echo "WARNING: GROQ_API_KEY is empty in .env."
    echo "  Receipt LLM parsing will fail until you set a free key from https://console.groq.com"
    echo "  Compose will still start; upload processing may land in PROCESSING_FAILED."
    echo
fi

echo "Starting Docker Compose (postgres + ocr + backend)..."
docker compose up --build -d

# First OCR image build can take several minutes; poll backend health until ready.
HEALTH_URL="http://localhost:8080/health"
MAX_ATTEMPTS=60
DELAY_SECONDS=5
echo "Waiting for ${HEALTH_URL} (up to ~$((MAX_ATTEMPTS * DELAY_SECONDS / 60)) minutes)..."
ready=0
for i in $(seq 1 "${MAX_ATTEMPTS}"); do
    if curl -fsS "${HEALTH_URL}" >/dev/null 2>&1; then
        ready=1
        break
    fi
    echo "  attempt ${i}/${MAX_ATTEMPTS} - not ready yet"
    sleep "${DELAY_SECONDS}"
done

if [[ "${ready}" -ne 1 ]]; then
    echo "Backend health check did not succeed in time. Check: docker compose logs" >&2
    exit 1
fi

echo "Backend health OK."
echo

echo "Installing frontend dependencies (npm install)..."
(
    cd "${REPO_ROOT}/frontend"
    npm install
)

echo
echo "Setup finished."
echo
echo "Next steps:"
echo "  1. Set GROQ_API_KEY in .env if you have not already (https://console.groq.com), then:"
echo "       docker compose up -d --force-recreate backend"
echo "  2. Start the frontend:"
echo "       cd frontend"
echo "       npm run dev"
echo "  3. Open http://localhost:5173"
echo
echo "Existing Postgres volumes / migration 003: see db/README.md"
echo "Backend health: http://localhost:8080/health"

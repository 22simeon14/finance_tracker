#!/usr/bin/env bash
# Verify MVP migrations on a clean temporary PostgreSQL container.
# Happy path only: apply 001 + 002, then run db/verify_happy_path.sql

set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-finance_tracker_verify_pg}"
POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:16}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-finance_tracker_verify}"
KEEP_CONTAINER="${KEEP_CONTAINER:-0}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cleanup() {
    if [[ "${KEEP_CONTAINER}" != "1" ]]; then
        docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
    fi
}

trap cleanup EXIT

wait_for_postgres() {
    echo "Waiting for PostgreSQL to become ready..."
    local ready=0
    for _ in $(seq 1 60); do
        if docker exec "${CONTAINER_NAME}" \
            psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -c "SELECT 1" >/dev/null 2>&1; then
            ready=1
            break
        fi
        sleep 1
    done

    if [[ "${ready}" -ne 1 ]]; then
        echo "PostgreSQL did not become ready in time." >&2
        exit 1
    fi
}

echo "Starting clean PostgreSQL (${POSTGRES_IMAGE})..."
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
docker run -d \
    --name "${CONTAINER_NAME}" \
    -e POSTGRES_USER="${POSTGRES_USER}" \
    -e POSTGRES_PASSWORD="${POSTGRES_PASSWORD}" \
    -e POSTGRES_DB="${POSTGRES_DB}" \
    "${POSTGRES_IMAGE}" >/dev/null

wait_for_postgres

run_psql_file() {
    local file_path="$1"
    local file_name
    file_name="$(basename "${file_path}")"
    local container_path="/tmp/${file_name}"

    docker cp "${file_path}" "${CONTAINER_NAME}:${container_path}"
    docker exec "${CONTAINER_NAME}" \
        psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -f "${container_path}"
}

echo "Applying 001_create_mvp_schema.sql..."
run_psql_file "${REPO_ROOT}/db/migrations/001_create_mvp_schema.sql"

echo "Applying 002_seed_categories.sql..."
run_psql_file "${REPO_ROOT}/db/migrations/002_seed_categories.sql"

echo "Running happy-path verification..."
run_psql_file "${REPO_ROOT}/db/verify_happy_path.sql"

echo
echo "Verification passed."
echo "Tables:"
docker exec "${CONTAINER_NAME}" \
    psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -c \
    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;"

if [[ "${KEEP_CONTAINER}" == "1" ]]; then
    echo
    echo "Container kept running: ${CONTAINER_NAME}"
    echo "Connect with:"
    echo "  docker exec -it ${CONTAINER_NAME} psql -U ${POSTGRES_USER} -d ${POSTGRES_DB}"
fi

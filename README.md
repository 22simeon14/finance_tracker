# finance_tracker
Web app that helps you analyze your spending

## Documentation

The system is documented in the [`docs/`](docs/) folder:

- **[`docs/architecture.md`](docs/architecture.md)** — main architecture doc (product scope, stack, packages, API, Flows A/B/C, data model, principles)
- **[`docs/diagrams/`](docs/diagrams/)** — detail Mermaid (and some SVG) diagrams linked from architecture.md
- **[`db/README.md`](db/README.md)** — SQL migrations and how to apply them

Start with `docs/architecture.md` if you are new to the repo.

## MVP Tech Stack
- Backend: Java 21+ + Spring Boot 3 (REST JSON API), JWT authentication (Spring Security), BCrypt password hashes
- Database: PostgreSQL (Docker) + SQL migrations in `db/migrations/` (currency **EUR only** after `003`)
- Persistence: Spring Data JPA (Hibernate), with schema controlled by SQL migrations
- Frontend: Vite + plain JavaScript (hash routing + `fetch`), register/login UI with JWT in the browser
- Document text: Apache PDFBox for digital PDFs; RapidOCR sidecar + Groq text-only header parse
- File uploads: local Docker volume
- Local orchestration: Docker Compose (**three services**: Postgres + RapidOCR sidecar + backend)

## Prerequisites
- Docker (Compose + enough disk for the first OCR image build)
- Node.js 20+
- JDK 21+ and Maven (only needed to run backend tests; no `mvnw` in this repo)
- A free [Groq API key](https://console.groq.com) for receipt LLM parsing (optional for Compose start; required for successful auto-extract)

## Environment variables

Copy [`.env.example`](.env.example) to `.env` (the setup scripts do this when `.env` is missing). Important values:

| Variable | Purpose |
| -------- | ------- |
| `POSTGRES_*` / `SPRING_DATASOURCE_URL` | Postgres credentials and JDBC URL (Compose hostname `postgres`) |
| `JWT_SECRET` | JWT signing key — change outside pure local use |
| `UPLOAD_DIR` | Upload path inside the backend container |
| `OCR_BASE_URL` / `OCR_TIMEOUT_MS` | RapidOCR sidecar URL (internal) and read timeout (~30s) |
| `GROQ_API_KEY` / `GROQ_API_BASE_URL` / `GROQ_MODEL` / `GROQ_TIMEOUT_MS` | Groq chat API; default model is `openai/gpt-oss-120b` (`llama-3.3-70b-versatile` is deprecated and returns 404) |
| `VITE_API_BASE_URL` | Optional direct API base for the SPA (host only) |

See `.env.example` for the full list and comments.

## Quick start (preferred)

From the repository root:

```powershell
.\scripts\setup.ps1
```

```bash
chmod +x scripts/setup.sh   # once, on Unix
./scripts/setup.sh
```

The script:

1. Copies `.env.example` → `.env` if `.env` is missing (never overwrites an existing `.env`)
2. Warns if `GROQ_API_KEY` is empty (does not invent a key)
3. Runs `docker compose up --build -d` (postgres + ocr + backend)
4. Polls `http://localhost:8080/health` until OK (first OCR build can take several minutes)
5. Runs `npm install` in `frontend/` (does **not** start the Vite server)

Then:

```powershell
# If needed: put your key in .env, then recreate the backend so it picks it up
docker compose up -d --force-recreate backend

cd frontend
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). Backend health: [http://localhost:8080/health](http://localhost:8080/health).

Existing Postgres volumes and applying migration `003`: see [db/README.md](db/README.md).

## Quick start (manual fallback)

1. Copy environment variables:

```powershell
Copy-Item .env.example .env
```

2. Set `GROQ_API_KEY` in `.env` (free key from [console.groq.com](https://console.groq.com)). Without it, `GroqReceiptParser` is not created and processing fails hard into `PROCESSING_FAILED`.

3. Start PostgreSQL, OCR sidecar, and backend (from repository root):

```powershell
docker compose up --build -d
```

First OCR image build downloads ONNX models and can take several minutes. The OCR port is not published; only the backend reaches it via `OCR_BASE_URL`.

Upload and retry (`POST /documents`, `POST /documents/{id}/process`) run OCR + Groq **synchronously** and can take **several seconds** (phone photos longer). OCR read timeout defaults to 30s, Groq to 20s; the Vite `/documents` proxy and Tomcat connection timeout allow up to ~120s so the browser is not cut off early.

4. In a second terminal, start the frontend on the host (frontend is **not** in Compose):

```powershell
cd frontend
npm install
npm run dev
```

5. Verify:

- Backend health: `curl http://localhost:8080/health` — expect `{"status":"ok","database":"up"}`
- Auth (no token): `curl http://localhost:8080/auth/me` — expect `401`
- Frontend: open `http://localhost:5173` — register or log in (`#/register`, `#/login`); home shows account status and backend health

**Note:** SQL migrations run automatically only on the first PostgreSQL volume creation (via `docker-entrypoint-initdb.d`). If the database volume already exists without schema, reset with `docker compose down -v` or apply migrations manually — see [db/README.md](db/README.md).

## Compose services

| Service | Role |
| ------- | ---- |
| `postgres` | PostgreSQL 16; migrations mounted into `docker-entrypoint-initdb.d` |
| `ocr` | RapidOCR sidecar (internal only — no host port) |
| `backend` | Spring Boot on host port `8080`; waits for postgres + ocr healthy |

## Demo walkthrough

End-to-end script (no manual DB steps). Prefer setup scripts above, then `cd frontend && npm run dev`, open [http://localhost:5173](http://localhost:5173).

1. **Register / Login** — `#/register` then `#/login` (or register alone if it already signs you in).
2. **Upload receipt** — `#/upload` (JPEG, PNG, or PDF ≤ 5 MB). Processing runs in the upload request and can take several seconds.
3. **Review** — correct merchant, date, amount, category. If status is `PROCESSING_FAILED`, use **Continue manually** then fill the form.
4. **Approve** — creates an expense and sets the document to `SAVED`.
5. **List + filters** — `#/expenses`; try from/to, category, and merchant filters.
6. **Dashboard** — `#/dashboard`; totals come only from **approved** expenses (pending/unapproved documents never appear in aggregates).
7. **Edit / unapprove** — open an expense (`#/expenses/{id}`), save changes, or **Unapprove** (expense gone; document returns to the pending inbox; file kept).

Forever-delete of a document + file is only from the pending inbox (`#/documents`), not from an approved expense.

## Tests

Backend unit tests (extraction pipeline) and API integration tests (Testcontainers PostgreSQL + MockMvc):

```text
cd backend && mvn test
```

Requires JDK 21, Maven, and Docker (for Testcontainers). OCR/Groq stay off in the test profile so the suite does not call the network.

For how the app is structured (auth, document processing, expenses, data model), see **[docs/architecture.md](docs/architecture.md)** and the diagrams under **[docs/diagrams/](docs/diagrams/)**.

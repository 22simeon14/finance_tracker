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
- Docker
- Node.js 20+
- JDK 21+

## Quick start

1. Copy environment variables:

```powershell
Copy-Item .env.example .env
```

2. Start PostgreSQL, OCR sidecar, and backend (from repository root):

```powershell
docker compose up --build
```

First OCR image build downloads ONNX models and can take several minutes. The OCR port is not published; only the backend reaches it via `OCR_BASE_URL`.

For receipt LLM parsing, set `GROQ_API_KEY` in `.env` (free key from [console.groq.com](https://console.groq.com)). Without it, `GroqReceiptParser` is not created and processing fails hard into `PROCESSING_FAILED`.

Upload and retry (`POST /documents`, `POST /documents/{id}/process`) run OCR + Groq **synchronously** and can take **several seconds** (phone photos longer). OCR read timeout defaults to 30s, Groq to 20s; the Vite `/documents` proxy and Tomcat connection timeout allow up to ~120s so the browser is not cut off early.

3. In a second terminal, start the frontend dev server on the host:

```powershell
cd frontend
npm install
npm run dev
```

4. Verify:

- Backend health: `curl http://localhost:8080/health` — expect `{"status":"ok","database":"up"}`
- Auth (no token): `curl http://localhost:8080/auth/me` — expect `401`
- Frontend: open `http://localhost:5173` — register or log in (`#/register`, `#/login`); home shows account status and backend health

**Note:** SQL migrations run automatically only on the first PostgreSQL volume creation (via `docker-entrypoint-initdb.d`). If the database volume already exists without schema, reset with `docker compose down -v` or apply migrations manually — see [db/README.md](db/README.md).

For how the app is structured (auth, document processing, expenses, data model), see **[docs/architecture.md](docs/architecture.md)** and the diagrams under **[docs/diagrams/](docs/diagrams/)**.

# finance_tracker
Web app that helps you analyze your spending

## MVP Tech Stack
- Backend: Java 21+ + Spring Boot 3 (REST JSON API), JWT authentication (Spring Security), BCrypt password hashes
- Database: PostgreSQL (Docker) + SQL migrations in `db/migrations/`
- Persistence: Spring Data JPA (Hibernate), with schema controlled by SQL migrations
- Frontend: Vite + plain JavaScript (hash routing + `fetch`), register/login UI with JWT in the browser
- File uploads: local Docker volume
- Local orchestration: Docker Compose

## Prerequisites
- Docker
- Node.js 20+
- JDK 21+

## Quick start

1. Copy environment variables:

```powershell
Copy-Item .env.example .env
```

2. Start PostgreSQL and backend (from repository root):

```powershell
docker compose up --build
```

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

More detail: [docs/architecture.md](docs/architecture.md) (Flow A — Authentication).

# Database

PostgreSQL is the MVP database. Schema and seeds live under `migrations/`.

## Apply locally

```bash
psql "$DATABASE_URL" -f db/migrations/001_create_mvp_schema.sql
psql "$DATABASE_URL" -f db/migrations/002_seed_categories.sql
psql "$DATABASE_URL" -f db/migrations/003_currency_eur_only.sql
```

Or from this directory:

```bash
psql "$DATABASE_URL" -f migrations/001_create_mvp_schema.sql
psql "$DATABASE_URL" -f migrations/002_seed_categories.sql
psql "$DATABASE_URL" -f migrations/003_currency_eur_only.sql
```

### Existing Compose volumes

Docker Compose only runs scripts under the Postgres init mount on **first** volume create. If your local database already exists from an earlier schema, `003_currency_eur_only.sql` will **not** run automatically.

Apply it once against the running database:

```bash
# Example when using the Compose postgres service
docker compose exec -T postgres psql -U postgres -d finance_tracker -f - < db/migrations/003_currency_eur_only.sql
```

Or with a direct `DATABASE_URL`:

```bash
psql "$DATABASE_URL" -f db/migrations/003_currency_eur_only.sql
```

Do **not** run `docker compose down -v` unless you intend to wipe local data and re-init from scratch.

## Verify on a clean PostgreSQL (happy path)

From the repository root, with Docker available:

```bash
bash db/verify_migrations.sh
```

On Windows PowerShell:

```powershell
.\db\verify_migrations.ps1
```

This script:

1. starts a temporary `postgres:16` container with an empty database;
2. applies `001_create_mvp_schema.sql`, `002_seed_categories.sql`, and `003_currency_eur_only.sql`;
3. runs `verify_happy_path.sql` (tables, indexes, seeded categories, EUR-only currency checks);
4. prints `\dt` and removes the container unless `KEEP_CONTAINER=1`.

To inspect the database after a successful run:

```bash
KEEP_CONTAINER=1 bash db/verify_migrations.sh
docker exec -it finance_tracker_verify_pg psql -U postgres -d finance_tracker_verify
```

The SQL migrations are the schema source of truth. Spring Data JPA entities map to these tables for reads/writes; Hibernate must not generate or alter DDL (`ddl-auto=none`).

See `docs/architecture.md` section 8 (Data model) for table responsibilities, invariants, and application business rules. Diagrams for extraction and document flows live under `docs/diagrams/`.

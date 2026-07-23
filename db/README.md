# Database

PostgreSQL is the MVP database. Schema and seeds live under `migrations/`.

## Apply locally

```bash
psql "$DATABASE_URL" -f db/migrations/001_create_mvp_schema.sql
psql "$DATABASE_URL" -f db/migrations/002_seed_categories.sql
```

Or from this directory:

```bash
psql "$DATABASE_URL" -f migrations/001_create_mvp_schema.sql
psql "$DATABASE_URL" -f migrations/002_seed_categories.sql
```

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
2. applies `001_create_mvp_schema.sql` and `002_seed_categories.sql`;
3. runs `verify_happy_path.sql` (tables, indexes, seeded categories);
4. prints `\dt` and removes the container unless `KEEP_CONTAINER=1`.

To inspect the database after a successful run:

```bash
KEEP_CONTAINER=1 bash db/verify_migrations.sh
docker exec -it finance_tracker_verify_pg psql -U postgres -d finance_tracker_verify
```

The SQL migrations are the schema source of truth. Spring Data JPA entities are added in later application steps; Hibernate must not generate DDL (`ddl-auto=none`).

See `docs/architecture.md` section 6.9 for table responsibilities, invariants, and application business rules.

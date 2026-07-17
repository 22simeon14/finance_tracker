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

ORM models are deferred until the application framework is chosen. The SQL migrations are the schema source of truth until then.

See `docs/architecture.md` section 6.9 for table responsibilities, invariants, and application business rules.

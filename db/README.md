# Main Responsibility: Local PostgreSQL schema notes and how to apply migrations.

PostgreSQL is the MVP database. Schema and seeds live under `migrations/`.

## Apply locally

Migrations are ordered: `001` schema → `002` category seed → `003` EUR-only currency checks → `004` category tree → `005` extraction line items → `006` line quantity/unit price → `007` confirmed expense lines.
**`001` alone still allows EUR/USD/GBP**; apply **`003`** for the MVP EUR-only rule. **`004`** adds `categories.parent_id` and the extra groups and leaves. **`005`** adds `document_extraction_lines`. **`006`** adds nullable `quantity` and `unit_price` on those lines. **`007`** adds `expense_lines` (same fields, cascading from `expenses`).

```bash
psql "$DATABASE_URL" -f db/migrations/001_create_mvp_schema.sql
psql "$DATABASE_URL" -f db/migrations/002_seed_categories.sql
psql "$DATABASE_URL" -f db/migrations/003_currency_eur_only.sql
psql "$DATABASE_URL" -f db/migrations/004_category_parent.sql
psql "$DATABASE_URL" -f db/migrations/005_document_extraction_lines.sql
psql "$DATABASE_URL" -f db/migrations/006_extraction_line_qty_unit_price.sql
psql "$DATABASE_URL" -f db/migrations/007_expense_lines.sql
```

Or from this directory:

```bash
psql "$DATABASE_URL" -f migrations/001_create_mvp_schema.sql
psql "$DATABASE_URL" -f migrations/002_seed_categories.sql
psql "$DATABASE_URL" -f migrations/003_currency_eur_only.sql
psql "$DATABASE_URL" -f migrations/004_category_parent.sql
psql "$DATABASE_URL" -f migrations/005_document_extraction_lines.sql
psql "$DATABASE_URL" -f migrations/006_extraction_line_qty_unit_price.sql
psql "$DATABASE_URL" -f migrations/007_expense_lines.sql
```

### Existing Compose volumes

Docker Compose only runs scripts under the Postgres init mount on **first** volume create. If your local database already exists from an earlier schema, later files such as `003_currency_eur_only.sql`, `004_category_parent.sql`, `005_document_extraction_lines.sql`, `006_extraction_line_qty_unit_price.sql`, and `007_expense_lines.sql` will **not** run automatically.

Apply each missing file once against the running database:

```bash
# Example when using the Compose postgres service
docker compose exec -T postgres psql -U postgres -d finance_tracker -f - < db/migrations/007_expense_lines.sql
```

Or with a direct `DATABASE_URL`:

```bash
psql "$DATABASE_URL" -f db/migrations/007_expense_lines.sql
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
2. applies `001` through `007` (`007` adds `expense_lines` under `expenses`);
3. runs `verify_happy_path.sql` (tables, indexes, seeded categories and parents, EUR-only currency checks);
4. prints `\dt` and removes the container unless `KEEP_CONTAINER=1`.

To inspect the database after a successful run:

```bash
KEEP_CONTAINER=1 bash db/verify_migrations.sh
docker exec -it finance_tracker_verify_pg psql -U postgres -d finance_tracker_verify
```

The SQL migrations are the schema source of truth. Spring Data JPA entities map to these tables for reads/writes; Hibernate must not generate or alter DDL (`ddl-auto=none`).

See `docs/architecture.md` section 8 (Data model) for table responsibilities, invariants, and application business rules. Diagrams for extraction and document flows live under `docs/diagrams/`.

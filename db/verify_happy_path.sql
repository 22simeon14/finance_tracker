-- Happy-path verification after 001 + 002 migrations.
-- Fails with RAISE EXCEPTION when an expectation is not met.
-- Run with: psql ... -v ON_ERROR_STOP=1 -f db/verify_happy_path.sql

\set ON_ERROR_STOP on

DO $$
DECLARE
    table_count INTEGER;
    index_count INTEGER;
    category_count INTEGER;
    inactive_count INTEGER;
    missing_slug_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO table_count
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name IN (
          'users',
          'categories',
          'documents',
          'document_extractions',
          'expenses'
      );

    IF table_count <> 5 THEN
        RAISE EXCEPTION 'Expected 5 MVP tables, found %', table_count;
    END IF;

    SELECT COUNT(*)
    INTO index_count
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND indexname IN (
          'documents_user_id_status_idx',
          'documents_user_id_created_at_idx',
          'expenses_expense_date_idx',
          'expenses_category_id_idx',
          'expenses_merchant_idx'
      );

    IF index_count <> 5 THEN
        RAISE EXCEPTION 'Expected 5 MVP indexes, found %', index_count;
    END IF;

    SELECT COUNT(*)
    INTO category_count
    FROM categories;

    IF category_count <> 10 THEN
        RAISE EXCEPTION 'Expected 10 seeded categories, found %', category_count;
    END IF;

    SELECT COUNT(*)
    INTO inactive_count
    FROM categories
    WHERE is_active = FALSE;

    IF inactive_count <> 0 THEN
        RAISE EXCEPTION 'Expected all seeded categories to be active, found % inactive', inactive_count;
    END IF;

    SELECT COUNT(*)
    INTO missing_slug_count
    FROM (
        VALUES
            ('food'),
            ('transport'),
            ('shopping'),
            ('housing'),
            ('health'),
            ('entertainment'),
            ('utilities'),
            ('travel'),
            ('education'),
            ('other')
    ) AS expected(slug)
    LEFT JOIN categories c ON c.slug = expected.slug
    WHERE c.slug IS NULL;

    IF missing_slug_count <> 0 THEN
        RAISE EXCEPTION 'Missing % expected category slug(s)', missing_slug_count;
    END IF;
END $$;

SELECT slug, name, is_active
FROM categories
ORDER BY id;

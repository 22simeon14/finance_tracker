-- Main Responsibility: Happy-path verification after 001 + 002 + 003 + 004 migrations.
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
    top_level_count INTEGER;
    leaf_count INTEGER;
    bad_parent_count INTEGER;
    nested_leaf_count INTEGER;
    expenses_currency_ok BOOLEAN;
    extractions_currency_ok BOOLEAN;
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
          'expenses_merchant_idx',
          'categories_parent_id_idx'
      );

    -- Includes categories_parent_id_idx from migration 004.
    IF index_count <> 6 THEN
        RAISE EXCEPTION 'Expected 6 MVP indexes, found %', index_count;
    END IF;

    SELECT COUNT(*)
    INTO category_count
    FROM categories;

    -- 10 original groups + 5 new groups + 12 leaves (004).
    IF category_count <> 27 THEN
        RAISE EXCEPTION 'Expected 27 seeded categories, found %', category_count;
    END IF;

    SELECT COUNT(*)
    INTO top_level_count
    FROM categories
    WHERE parent_id IS NULL;

    IF top_level_count <> 15 THEN
        RAISE EXCEPTION 'Expected 15 top-level categories, found %', top_level_count;
    END IF;

    SELECT COUNT(*)
    INTO leaf_count
    FROM categories
    WHERE parent_id IS NOT NULL;

    IF leaf_count <> 12 THEN
        RAISE EXCEPTION 'Expected 12 category leaves, found %', leaf_count;
    END IF;

    SELECT COUNT(*)
    INTO bad_parent_count
    FROM (
        VALUES
            ('meat', 'food'),
            ('deli', 'food'),
            ('sweets', 'food'),
            ('alcohol', 'food'),
            ('produce', 'food'),
            ('dairy', 'food'),
            ('bakery', 'food'),
            ('toiletries', 'household'),
            ('cleaning', 'household'),
            ('fuel', 'transport'),
            ('public-transport', 'transport'),
            ('pharmacy', 'health')
    ) AS expected(child_slug, parent_slug)
    LEFT JOIN categories child ON child.slug = expected.child_slug
    LEFT JOIN categories parent ON parent.slug = expected.parent_slug
    WHERE child.parent_id IS DISTINCT FROM parent.id;

    IF bad_parent_count <> 0 THEN
        RAISE EXCEPTION 'Expected every leaf to point at its parent slug, found % mismatches', bad_parent_count;
    END IF;

    SELECT COUNT(*)
    INTO nested_leaf_count
    FROM categories child
    JOIN categories parent ON child.parent_id = parent.id
    WHERE parent.parent_id IS NOT NULL;

    IF nested_leaf_count <> 0 THEN
        RAISE EXCEPTION 'Expected one category level only, found % nested leaves', nested_leaf_count;
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
            ('other'),
            ('household'),
            ('eating-out'),
            ('insurance'),
            ('subscriptions'),
            ('sport'),
            ('meat'),
            ('deli'),
            ('sweets'),
            ('alcohol'),
            ('produce'),
            ('dairy'),
            ('bakery'),
            ('toiletries'),
            ('cleaning'),
            ('fuel'),
            ('public-transport'),
            ('pharmacy')
    ) AS expected(slug)
    LEFT JOIN categories c ON c.slug = expected.slug
    WHERE c.slug IS NULL;

    IF missing_slug_count <> 0 THEN
        RAISE EXCEPTION 'Missing % expected category slug(s)', missing_slug_count;
    END IF;

    -- After 003, only EUR is allowed (proposed_currency may still be NULL).
    SELECT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'expenses_currency_check'
          AND pg_get_constraintdef(oid) LIKE '%EUR%'
          AND pg_get_constraintdef(oid) NOT LIKE '%USD%'
          AND pg_get_constraintdef(oid) NOT LIKE '%GBP%'
    )
    INTO expenses_currency_ok;

    IF NOT expenses_currency_ok THEN
        RAISE EXCEPTION 'expenses_currency_check must allow EUR only';
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'document_extractions_proposed_currency_check'
          AND pg_get_constraintdef(oid) LIKE '%EUR%'
          AND pg_get_constraintdef(oid) NOT LIKE '%USD%'
          AND pg_get_constraintdef(oid) NOT LIKE '%GBP%'
    )
    INTO extractions_currency_ok;

    IF NOT extractions_currency_ok THEN
        RAISE EXCEPTION 'document_extractions_proposed_currency_check must allow EUR only (or NULL)';
    END IF;
END $$;

SELECT slug, name, is_active
FROM categories
ORDER BY id;

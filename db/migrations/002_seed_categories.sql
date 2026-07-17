-- Seed default MVP categories.
-- Exact product labels may be refined later; slugs are stable API/filter codes.

BEGIN;

INSERT INTO categories (name, slug, is_active)
VALUES
    ('Food & Drink', 'food', TRUE),
    ('Transport', 'transport', TRUE),
    ('Shopping', 'shopping', TRUE),
    ('Housing', 'housing', TRUE),
    ('Health', 'health', TRUE),
    ('Entertainment', 'entertainment', TRUE),
    ('Utilities', 'utilities', TRUE),
    ('Travel', 'travel', TRUE),
    ('Education', 'education', TRUE),
    ('Other', 'other', TRUE);

COMMIT;

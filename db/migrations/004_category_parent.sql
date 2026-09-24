-- Main Responsibility: Add category parent_id and seed groups plus leaves.
--
-- parent_id null = top-level group (the expense header). A set parent_id is a
-- leaf used later on a receipt line. One level only: a leaf's parent is a group.
-- Existing Compose volumes do not re-run init scripts. Apply this file once
-- with psql against the live database (see db/README.md).

BEGIN;

ALTER TABLE categories
    ADD COLUMN parent_id BIGINT;

-- Deleting a group that still has leaves is rejected.
ALTER TABLE categories
    ADD CONSTRAINT categories_parent_id_fk
        FOREIGN KEY (parent_id) REFERENCES categories (id) ON DELETE RESTRICT;

ALTER TABLE categories
    ADD CONSTRAINT categories_parent_id_not_self
        CHECK (parent_id IS NULL OR parent_id <> id);

CREATE INDEX categories_parent_id_idx
    ON categories (parent_id);

-- New groups. Shopping, Travel, Education, and Other stay childless.
INSERT INTO categories (name, slug, is_active)
VALUES
    ('Household', 'household', TRUE),
    ('Eating out', 'eating-out', TRUE),
    ('Insurance', 'insurance', TRUE),
    ('Subscriptions', 'subscriptions', TRUE),
    ('Sport', 'sport', TRUE);

-- Leaves. Slugs stay stable; names are the labels shown in the UI.
INSERT INTO categories (name, slug, is_active, parent_id)
SELECT leaf.name, leaf.slug, TRUE, parent.id
FROM (
    VALUES
        ('Meat', 'meat', 'food'),
        ('Deli', 'deli', 'food'),
        ('Sweets', 'sweets', 'food'),
        ('Alcohol', 'alcohol', 'food'),
        ('Produce', 'produce', 'food'),
        ('Dairy', 'dairy', 'food'),
        ('Bakery', 'bakery', 'food'),
        ('Toiletries', 'toiletries', 'household'),
        ('Cleaning', 'cleaning', 'household'),
        ('Fuel', 'fuel', 'transport'),
        ('Public transport', 'public-transport', 'transport'),
        ('Pharmacy', 'pharmacy', 'health')
) AS leaf(name, slug, parent_slug)
JOIN categories parent ON parent.slug = leaf.parent_slug;

COMMIT;

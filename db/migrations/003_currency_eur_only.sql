-- Main Responsibility: Restrict expense and extraction currency to EUR only.
--
-- Existing Compose volumes do not re-run init scripts. Apply this file once
-- with psql against the live database (see db/README.md). Do not use
-- docker compose down -v unless you intend to wipe local data.

BEGIN;

-- Coerce any legacy non-EUR values before the tighter checks are added.
UPDATE document_extractions
SET proposed_currency = 'EUR'
WHERE proposed_currency IS NOT NULL
  AND proposed_currency <> 'EUR';

UPDATE expenses
SET currency = 'EUR'
WHERE currency <> 'EUR';

ALTER TABLE document_extractions
    DROP CONSTRAINT document_extractions_proposed_currency_check;

ALTER TABLE document_extractions
    ADD CONSTRAINT document_extractions_proposed_currency_check
        CHECK (
            proposed_currency IS NULL
            OR proposed_currency = 'EUR'
        );

ALTER TABLE expenses
    DROP CONSTRAINT expenses_currency_check;

ALTER TABLE expenses
    ADD CONSTRAINT expenses_currency_check
        CHECK (currency = 'EUR');

COMMIT;

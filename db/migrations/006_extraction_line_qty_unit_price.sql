-- Main Responsibility: Add optional quantity and unit_price on extraction lines.
--
-- amount stays the line total (> 0). qty and unit_price are nullable; no SQL
-- CHECK that qty × unit_price equals amount (OCR soft mismatches are OK).
-- Existing Compose volumes do not re-run init scripts — apply once with psql
-- against the live database (see db/README.md).

BEGIN;

ALTER TABLE document_extraction_lines
    ADD COLUMN quantity DECIMAL(12, 3),
    ADD COLUMN unit_price DECIMAL(12, 2);

-- Soft positivity only: null or > 0 (never zero/negative when present).
ALTER TABLE document_extraction_lines
    ADD CONSTRAINT document_extraction_lines_quantity_positive
        CHECK (quantity IS NULL OR quantity > 0),
    ADD CONSTRAINT document_extraction_lines_unit_price_positive
        CHECK (unit_price IS NULL OR unit_price > 0);

COMMIT;

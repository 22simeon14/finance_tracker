package com.financetracker.document;

import java.math.BigDecimal;

/**
 * Main Responsibility: One proposed line item in the review JSON.
 *
 * Mirrors persisted document_extraction_lines fields the UI needs —
 * description, optional quantity/unitPrice, amount (line total), and
 * optional categoryId (no internal row id).
 */
public record ExtractionLineResponse(
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        Long categoryId
) {
}

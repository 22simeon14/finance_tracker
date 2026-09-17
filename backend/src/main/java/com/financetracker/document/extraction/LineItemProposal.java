package com.financetracker.document.extraction;

import java.math.BigDecimal;

/**
 * Main Responsibility: One proposed line item from extraction (not persisted yet).
 *
 * ExtractionResult always carries an empty list in this milestone so a later
 * step can reuse the same result shape without changing DocumentService.
 */
public record LineItemProposal(String description, BigDecimal amount) {
}

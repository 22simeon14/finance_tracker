package com.financetracker.document.extraction;

import java.math.BigDecimal;

/**
 * Main Responsibility: One proposed line item from extraction (before persist).
 *
 * categoryId is set when categorySlug matched an active category; otherwise null.
 * DocumentService writes these into document_extraction_lines for review GET.
 */
public record LineItemProposal(String description, BigDecimal amount, Long categoryId) {
}

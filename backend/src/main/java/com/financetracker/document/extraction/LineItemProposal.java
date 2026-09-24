package com.financetracker.document.extraction;

import java.math.BigDecimal;

/**
 * Main Responsibility: One proposed line item from extraction (in memory only).
 *
 * categoryId is set when categorySlug matched an active category; otherwise null.
 * Persistence and review UI come in later steps.
 */
public record LineItemProposal(String description, BigDecimal amount, Long categoryId) {
}

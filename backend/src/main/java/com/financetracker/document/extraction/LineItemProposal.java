package com.financetracker.document.extraction;

import java.math.BigDecimal;

/**
 * Main Responsibility: One proposed line item from extraction (before persist).
 *
 * amount is the line total. quantity and unitPrice are optional (null when
 * OCR/LLM did not provide them). categoryId is set when categorySlug matched
 * an active category; otherwise null. DocumentService writes these into
 * document_extraction_lines for review GET.
 */
public record LineItemProposal(
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        Long categoryId
) {
    /**
     * Build a proposal when quantity and unit price are unknown.
     */
    public static LineItemProposal of(String description, BigDecimal amount, Long categoryId) {
        return new LineItemProposal(description, null, null, amount, categoryId);
    }
}

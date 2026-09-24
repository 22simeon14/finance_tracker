package com.financetracker.document.extraction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Main Responsibility: Header and line-item proposals from OCR + LLM parse.
 *
 * lineItems live in memory for this step; DocumentService still persists only
 * header fields onto document_extractions until a later migration.
 */
public record ExtractionResult(
        String rawOcrText,
        String merchant,
        LocalDate date,
        BigDecimal totalAmount,
        String currency,
        Long categoryId,
        List<LineItemProposal> lineItems
) {

    public ExtractionResult {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
    }

    /** Convenience when the caller has no line items (or an empty list). */
    public static ExtractionResult ofHeaders(
            String rawOcrText,
            String merchant,
            LocalDate date,
            BigDecimal totalAmount,
            String currency,
            Long categoryId
    ) {
        return new ExtractionResult(
                rawOcrText,
                merchant,
                date,
                totalAmount,
                currency,
                categoryId,
                List.of()
        );
    }

    /**
     * True when at least one header field is useful for review.
     * Currency alone does not count (validator always forces EUR).
     * Line items alone do not count — a receipt with no header can still go to review.
     */
    public boolean hasUsableHeader() {
        return isPresent(merchant) || date != null || totalAmount != null || categoryId != null;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}

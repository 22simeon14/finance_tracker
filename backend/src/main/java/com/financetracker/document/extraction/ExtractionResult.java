package com.financetracker.document.extraction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Main Responsibility: Header proposals from OCR + LLM parse (before human review).
 *
 * lineItems is always empty in this milestone and is not mapped to the API or DB yet.
 * DocumentService persists only the header fields onto document_extractions.
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

    /** Header-only result with a forced empty line-item list. */
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
     */
    public boolean hasUsableHeader() {
        return isPresent(merchant) || date != null || totalAmount != null || categoryId != null;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}

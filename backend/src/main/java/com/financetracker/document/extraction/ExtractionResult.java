package com.financetracker.document.extraction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Main Responsibility: Header and line-item proposals from OCR + LLM parse.
 *
 * DocumentService maps headers onto document_extractions and lineItems onto
 * document_extraction_lines for the review JSON.
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
     * Line items alone do not count — DocumentService still marks PROCESSING_FAILED
     * when no header field is usable (user can retry or continue manually).
     */
    public boolean hasUsableHeader() {
        return isPresent(merchant) || date != null || totalAmount != null || categoryId != null;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}

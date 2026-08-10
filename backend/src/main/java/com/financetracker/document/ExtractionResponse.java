package com.financetracker.document;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Main Responsibility: JSON DTO for proposed extraction fields on the review form.
 *
 * Exposes OCR text and proposed merchant/date/amount/currency/category —
 * not internal extraction id or timestamps.
 */
public record ExtractionResponse(
        String rawOcrText,
        String proposedMerchant,
        LocalDate proposedDate,
        BigDecimal proposedAmount,
        String proposedCurrency,
        Long proposedCategoryId
) {
}

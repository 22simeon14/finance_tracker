package com.financetracker.document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Main Responsibility: JSON DTO for proposed extraction fields on the review form.
 *
 * Exposes OCR text, proposed merchant/date/amount/currency/category, and
 * proposed line items — not internal extraction id or timestamps.
 */
public record ExtractionResponse(
        String rawOcrText,
        String proposedMerchant,
        LocalDate proposedDate,
        BigDecimal proposedAmount,
        String proposedCurrency,
        Long proposedCategoryId,
        List<ExtractionLineResponse> lineItems
) {
}

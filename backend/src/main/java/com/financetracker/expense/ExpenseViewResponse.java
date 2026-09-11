package com.financetracker.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Main Responsibility: Public read DTO for expense list items and detail views.
 *
 * Extends approve fields with category name and document file metadata for the UI.
 * Approve still returns ExpenseResponse unchanged.
 */
public record ExpenseViewResponse(
        Long id,
        Long documentId,
        Long categoryId,
        String categoryName,
        String merchant,
        LocalDate expenseDate,
        BigDecimal totalAmount,
        String currency,
        LocalDateTime createdAt,
        String documentFileUrl,
        String originalFilename
) {
}

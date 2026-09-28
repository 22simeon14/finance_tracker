package com.financetracker.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Main Responsibility: Public read DTO for expense list items and detail views.
 *
 * Extends approve fields with category name, document file metadata, and
 * lineItems. Detail (GET/PUT by id) loads confirmed expense_lines; the list
 * endpoint returns an empty lineItems array so the UI stays one row per purchase.
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
        String originalFilename,
        List<ExpenseLineResponse> lineItems
) {
}

package com.financetracker.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Main Responsibility: JSON response after approving a document into an expense.
 *
 * Public fields only — no internal storage paths or user id.
 */
public record ExpenseResponse(
        Long id,
        Long documentId,
        Long categoryId,
        String merchant,
        LocalDate expenseDate,
        BigDecimal totalAmount,
        String currency,
        LocalDateTime createdAt
) {
}

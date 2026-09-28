package com.financetracker.expense;

import java.math.BigDecimal;

/**
 * Main Responsibility: One confirmed line item in expense GET/PUT JSON.
 *
 * Same shape as ExtractionLineResponse / ExpenseLineRequest fields the UI
 * needs — description, optional quantity/unitPrice, amount, optional categoryId.
 */
public record ExpenseLineResponse(
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        Long categoryId
) {
}

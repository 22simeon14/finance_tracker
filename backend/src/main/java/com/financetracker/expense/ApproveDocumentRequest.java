package com.financetracker.expense;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Main Responsibility: Validated JSON body for POST /documents/{id}/approve.
 *
 * Confirmed review fields become the expense row. Merchant is optional;
 * blank values are normalized to null in ExpenseService. Currency is EUR only.
 */
public record ApproveDocumentRequest(
        @NotNull LocalDate expenseDate,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal totalAmount,
        @NotNull @Pattern(regexp = "EUR") String currency,
        @NotNull Long categoryId,
        @Size(max = 255) String merchant
) {
}

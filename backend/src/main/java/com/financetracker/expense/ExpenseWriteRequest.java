package com.financetracker.expense;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Main Responsibility: Validated JSON body for PUT /expenses/{id}.
 *
 * Same confirmed fields as approve so edit reuses the same rules
 * (amount > 0, currency allowlist, active category, optional merchant).
 */
public record ExpenseWriteRequest(
        @NotNull LocalDate expenseDate,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal totalAmount,
        @NotNull @Pattern(regexp = "EUR|USD|GBP") String currency,
        @NotNull Long categoryId,
        @Size(max = 255) String merchant
) {
}

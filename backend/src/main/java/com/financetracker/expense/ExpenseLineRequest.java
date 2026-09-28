package com.financetracker.expense;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Main Responsibility: One confirmed line in the approve JSON body.
 *
 * Comes from the review form, not from document_extraction_lines.
 * Description and a positive amount are required. Quantity and unit price
 * are optional (null when the form left them blank). categoryId may be null;
 * when set, ExpenseService requires an active category.
 */
public record ExpenseLineRequest(
        @NotBlank @Size(max = 255) String description,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal quantity,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal amount,
        Long categoryId
) {
}

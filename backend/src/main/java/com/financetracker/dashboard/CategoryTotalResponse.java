package com.financetracker.dashboard;

import java.math.BigDecimal;

/**
 * Main Responsibility: One category+currency bucket in the dashboard breakdown.
 *
 * categoryName comes from the categories table (including inactive names).
 */
public record CategoryTotalResponse(
        Long categoryId,
        String categoryName,
        String currency,
        BigDecimal totalAmount
) {
}

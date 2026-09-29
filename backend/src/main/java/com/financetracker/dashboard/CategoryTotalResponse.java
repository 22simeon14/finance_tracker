package com.financetracker.dashboard;

import java.math.BigDecimal;

/**
 * Main Responsibility: One category+currency bucket in the dashboard.
 *
 * Used for line-based byParentCategory roll-ups (By group).
 * categoryName comes from the categories table (including inactive names).
 */
public record CategoryTotalResponse(
        Long categoryId,
        String categoryName,
        String currency,
        BigDecimal totalAmount
) {
}

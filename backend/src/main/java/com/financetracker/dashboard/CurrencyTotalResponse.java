package com.financetracker.dashboard;

import java.math.BigDecimal;

/**
 * Main Responsibility: One currency bucket in the dashboard totals section.
 *
 * Currencies are never merged into a single fake total.
 */
public record CurrencyTotalResponse(
        String currency,
        BigDecimal totalAmount
) {
}

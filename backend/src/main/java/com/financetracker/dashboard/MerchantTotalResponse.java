package com.financetracker.dashboard;

import java.math.BigDecimal;

/**
 * Main Responsibility: One merchant+currency bucket in the dashboard breakdown.
 *
 * Missing merchants are labeled "(none)" so null/blank share one bucket.
 */
public record MerchantTotalResponse(
        String merchant,
        String currency,
        BigDecimal totalAmount
) {
}

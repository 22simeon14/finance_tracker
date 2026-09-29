package com.financetracker.dashboard;

import java.util.List;

/**
 * Main Responsibility: Full JSON body for GET /dashboard.
 *
 * Aggregates come only from confirmed expenses (not extractions or pending docs).
 * Each list stays split by currency — no cross-currency sum.
 * byLeafCategory / byParentCategory are the line-item breakdown (leaves,
 * unallocated remainder, parent roll-ups).
 */
public record DashboardResponse(
        List<CurrencyTotalResponse> totalsByCurrency,
        List<LineCategoryTotalResponse> byLeafCategory,
        List<CategoryTotalResponse> byParentCategory,
        List<MerchantTotalResponse> byMerchant
) {
}

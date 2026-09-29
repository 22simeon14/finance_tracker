package com.financetracker.dashboard;

import java.math.BigDecimal;

/**
 * Main Responsibility: One leaf (or unallocated) bucket in the line-based
 * category report.
 *
 * parentId is the top-level group this amount rolls into. Unallocated rows use
 * categoryId null and unallocated true; their parentId is the expense header
 * group. Used for the by-leaf list and for pie slices of groups that have
 * real subcategories. Childless groups never appear here.
 */
public record LineCategoryTotalResponse(
        Long categoryId,
        String categoryName,
        Long parentId,
        String parentName,
        String currency,
        BigDecimal totalAmount,
        boolean unallocated
) {
}

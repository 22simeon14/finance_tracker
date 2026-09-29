package com.financetracker.dashboard;

import com.financetracker.category.Category;
import com.financetracker.expense.Expense;
import com.financetracker.expense.ExpenseLine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main Responsibility: Turn expenses + expense_lines into leaf and parent
 * category totals for the dashboard line breakdown.
 *
 * No lines → whole total goes to the expense header group (not labeled
 * unallocated). With lines → real leaves roll up to their parent. A line that
 * points at a top-level group with leaves (e.g. food) counts as Unallocated
 * under that group — never as a fake leaf named like the group. A line that
 * points at a childless group (e.g. Shopping) adds only the parent roll-up —
 * no leaf row, so the pie has nothing to split. Positive total − line sum is
 * unallocated under the expense header group. Overrun invents no negative slice.
 */
public final class CategoryBreakdownCalculator {

    private static final String UNALLOCATED_NAME = "Unallocated";

    private CategoryBreakdownCalculator() {
    }

    /**
     * Aggregate leaf and parent buckets for the given expenses.
     * categoriesById must include every category id referenced by expenses/lines
     * (inactive names are fine). Missing line categories count as unallocated
     * under the expense header group.
     */
    public static Result compute(
            List<Expense> expenses,
            Map<Long, List<ExpenseLine>> linesByExpenseId,
            Map<Long, Category> categoriesById
    ) {
        Map<BucketKey, BigDecimal> leafTotals = new HashMap<>();
        Map<BucketKey, LeafMeta> leafMeta = new HashMap<>();
        Map<ParentKey, BigDecimal> parentTotals = new HashMap<>();
        Map<ParentKey, String> parentNames = new HashMap<>();

        for (Expense expense : expenses) {
            Category expenseCategory = categoriesById.get(expense.getCategoryId());
            if (expenseCategory == null) {
                continue;
            }
            // Header category is always a top-level group; if a leaf slipped in, use its parent.
            Long expenseGroupId = resolveGroupId(expenseCategory);
            Category expenseGroup = categoriesById.get(expenseGroupId);
            if (expenseGroup == null) {
                continue;
            }
            String currency = expense.getCurrency();
            List<ExpenseLine> lines = linesByExpenseId.getOrDefault(expense.getId(), List.of());

            if (lines.isEmpty()) {
                addParent(parentTotals, parentNames, expenseGroupId, expenseGroup.getName(), currency, expense.getTotalAmount());
                continue;
            }

            BigDecimal lineSum = BigDecimal.ZERO;
            for (ExpenseLine line : lines) {
                lineSum = lineSum.add(line.getAmount());
                allocateLine(
                        line,
                        expenseGroupId,
                        expenseGroup.getName(),
                        currency,
                        categoriesById,
                        leafTotals,
                        leafMeta,
                        parentTotals,
                        parentNames
                );
            }

            // Only a positive remainder is unallocated; overrun invents no minus slice.
            BigDecimal remainder = expense.getTotalAmount().subtract(lineSum);
            if (remainder.compareTo(BigDecimal.ZERO) > 0) {
                addUnallocated(
                        leafTotals,
                        leafMeta,
                        parentTotals,
                        parentNames,
                        expenseGroupId,
                        expenseGroup.getName(),
                        currency,
                        remainder
                );
            }
        }

        return new Result(toLeafList(leafTotals, leafMeta), toParentList(parentTotals, parentNames));
    }

    /**
     * Place one line amount into leaf + parent buckets.
     * Null/unknown category → unallocated under the expense header group.
     * Top-level with leaves (e.g. food) → Unallocated under that group (OCR
     * often returns the parent slug; it must not become a pie slice named Food).
     * Top-level without leaves (e.g. Shopping) → parent total only; no leaf
     * and no pie slice (nothing to break down).
     * Leaf category → leaf under its parent, parent gets the roll-up.
     */
    private static void allocateLine(
            ExpenseLine line,
            Long expenseGroupId,
            String expenseGroupName,
            String currency,
            Map<Long, Category> categoriesById,
            Map<BucketKey, BigDecimal> leafTotals,
            Map<BucketKey, LeafMeta> leafMeta,
            Map<ParentKey, BigDecimal> parentTotals,
            Map<ParentKey, String> parentNames
    ) {
        Category lineCategory = line.getCategoryId() == null
                ? null
                : categoriesById.get(line.getCategoryId());

        if (lineCategory == null) {
            addUnallocated(
                    leafTotals,
                    leafMeta,
                    parentTotals,
                    parentNames,
                    expenseGroupId,
                    expenseGroupName,
                    currency,
                    line.getAmount()
            );
            return;
        }

        if (lineCategory.getParentId() == null) {
            Long groupId = lineCategory.getId();
            String groupName = lineCategory.getName();
            if (groupHasLeaves(groupId, categoriesById)) {
                // Parent slug on a line is not a real leaf — treat as unallocated.
                addUnallocated(
                        leafTotals,
                        leafMeta,
                        parentTotals,
                        parentNames,
                        groupId,
                        groupName,
                        currency,
                        line.getAmount()
                );
                return;
            }
            // Childless group: roll into By group only — no fake leaf for a 100% pie.
            addParent(parentTotals, parentNames, groupId, groupName, currency, line.getAmount());
            return;
        }

        Category parent = categoriesById.get(lineCategory.getParentId());
        if (parent == null) {
            addUnallocated(
                    leafTotals,
                    leafMeta,
                    parentTotals,
                    parentNames,
                    expenseGroupId,
                    expenseGroupName,
                    currency,
                    line.getAmount()
            );
            return;
        }

        addLeaf(
                leafTotals,
                leafMeta,
                lineCategory.getId(),
                lineCategory.getName(),
                parent.getId(),
                parent.getName(),
                currency,
                line.getAmount(),
                false
        );
        addParent(parentTotals, parentNames, parent.getId(), parent.getName(), currency, line.getAmount());
    }

    /** True when any category lists this id as parent (group has real leaves). */
    private static boolean groupHasLeaves(Long groupId, Map<Long, Category> categoriesById) {
        for (Category category : categoriesById.values()) {
            if (groupId.equals(category.getParentId())) {
                return true;
            }
        }
        return false;
    }

    private static void addUnallocated(
            Map<BucketKey, BigDecimal> leafTotals,
            Map<BucketKey, LeafMeta> leafMeta,
            Map<ParentKey, BigDecimal> parentTotals,
            Map<ParentKey, String> parentNames,
            Long groupId,
            String groupName,
            String currency,
            BigDecimal amount
    ) {
        addLeaf(
                leafTotals,
                leafMeta,
                null,
                UNALLOCATED_NAME,
                groupId,
                groupName,
                currency,
                amount,
                true
        );
        addParent(parentTotals, parentNames, groupId, groupName, currency, amount);
    }

    private static void addLeaf(
            Map<BucketKey, BigDecimal> leafTotals,
            Map<BucketKey, LeafMeta> leafMeta,
            Long categoryId,
            String categoryName,
            Long parentId,
            String parentName,
            String currency,
            BigDecimal amount,
            boolean unallocated
    ) {
        BucketKey key = new BucketKey(categoryId, parentId, currency, unallocated);
        leafTotals.merge(key, amount, BigDecimal::add);
        leafMeta.putIfAbsent(key, new LeafMeta(categoryName, parentName));
    }

    private static void addParent(
            Map<ParentKey, BigDecimal> parentTotals,
            Map<ParentKey, String> parentNames,
            Long categoryId,
            String categoryName,
            String currency,
            BigDecimal amount
    ) {
        ParentKey key = new ParentKey(categoryId, currency);
        parentTotals.merge(key, amount, BigDecimal::add);
        parentNames.putIfAbsent(key, categoryName);
    }

    /** Top-level group id for an expense header category. */
    private static Long resolveGroupId(Category category) {
        return category.getParentId() != null ? category.getParentId() : category.getId();
    }

    private static List<LineCategoryTotalResponse> toLeafList(
            Map<BucketKey, BigDecimal> leafTotals,
            Map<BucketKey, LeafMeta> leafMeta
    ) {
        List<LineCategoryTotalResponse> rows = new ArrayList<>(leafTotals.size());
        for (Map.Entry<BucketKey, BigDecimal> entry : leafTotals.entrySet()) {
            BucketKey key = entry.getKey();
            LeafMeta meta = leafMeta.get(key);
            rows.add(new LineCategoryTotalResponse(
                    key.categoryId(),
                    meta.categoryName(),
                    key.parentId(),
                    meta.parentName(),
                    key.currency(),
                    entry.getValue(),
                    key.unallocated()
            ));
        }
        rows.sort(Comparator
                .comparing(LineCategoryTotalResponse::totalAmount).reversed()
                .thenComparing(LineCategoryTotalResponse::categoryName, Comparator.nullsLast(String::compareTo))
                .thenComparing(LineCategoryTotalResponse::currency));
        return rows;
    }

    private static List<CategoryTotalResponse> toParentList(
            Map<ParentKey, BigDecimal> parentTotals,
            Map<ParentKey, String> parentNames
    ) {
        List<CategoryTotalResponse> rows = new ArrayList<>(parentTotals.size());
        for (Map.Entry<ParentKey, BigDecimal> entry : parentTotals.entrySet()) {
            ParentKey key = entry.getKey();
            rows.add(new CategoryTotalResponse(
                    key.categoryId(),
                    parentNames.get(key),
                    key.currency(),
                    entry.getValue()
            ));
        }
        rows.sort(Comparator
                .comparing(CategoryTotalResponse::totalAmount).reversed()
                .thenComparing(CategoryTotalResponse::categoryName)
                .thenComparing(CategoryTotalResponse::currency));
        return rows;
    }

    /** Leaf + parent lists for GET /dashboard. */
    public record Result(
            List<LineCategoryTotalResponse> byLeafCategory,
            List<CategoryTotalResponse> byParentCategory
    ) {
    }

    private record BucketKey(Long categoryId, Long parentId, String currency, boolean unallocated) {
    }

    private record LeafMeta(String categoryName, String parentName) {
    }

    private record ParentKey(Long categoryId, String currency) {
    }
}

package com.financetracker.dashboard;

import com.financetracker.category.Category;
import com.financetracker.expense.Expense;
import com.financetracker.expense.ExpenseLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Main Responsibility: Prove line-based category totals match the stage-1
 * breakdown rules (leaf roll-up, positive unallocated, no negative slice).
 */
class CategoryBreakdownCalculatorTest {

    @Test
    void lidlReceipt_splitsFoodUnallocatedAndHousehold() {
        Category food = group(1L, "Food & Drink");
        Category household = group(2L, "Household");
        Category meat = leaf(11L, "Meat", food.getId());
        Category toiletries = leaf(21L, "Toiletries", household.getId());

        Expense expense = expense(100L, food.getId(), "47.00", "EUR");
        List<ExpenseLine> lines = List.of(
                line(100L, "Minced meat", "8.00", meat.getId()),
                line(100L, "Soap", "4.00", toiletries.getId())
        );

        CategoryBreakdownCalculator.Result result = CategoryBreakdownCalculator.compute(
                List.of(expense),
                Map.of(100L, lines),
                Map.of(
                        food.getId(), food,
                        household.getId(), household,
                        meat.getId(), meat,
                        toiletries.getId(), toiletries
                )
        );

        assertEquals(3, result.byLeafCategory().size());
        LineCategoryTotalResponse meatRow = findLeaf(result, "Meat");
        assertEquals(0, new BigDecimal("8.00").compareTo(meatRow.totalAmount()));
        assertEquals(food.getId(), meatRow.parentId());
        assertFalse(meatRow.unallocated());

        LineCategoryTotalResponse toiletriesRow = findLeaf(result, "Toiletries");
        assertEquals(0, new BigDecimal("4.00").compareTo(toiletriesRow.totalAmount()));
        assertEquals(household.getId(), toiletriesRow.parentId());

        LineCategoryTotalResponse unallocated = findUnallocated(result, food.getId());
        assertEquals(0, new BigDecimal("35.00").compareTo(unallocated.totalAmount()));
        assertNull(unallocated.categoryId());
        assertEquals(food.getId(), unallocated.parentId());

        assertEquals(2, result.byParentCategory().size());
        CategoryTotalResponse foodParent = findParent(result, "Food & Drink");
        CategoryTotalResponse householdParent = findParent(result, "Household");
        assertEquals(0, new BigDecimal("43.00").compareTo(foodParent.totalAmount()));
        assertEquals(0, new BigDecimal("4.00").compareTo(householdParent.totalAmount()));
        assertEquals(
                0,
                new BigDecimal("47.00").compareTo(foodParent.totalAmount().add(householdParent.totalAmount()))
        );
    }

    @Test
    void linesExceedTotal_doesNotInventNegativeUnallocated() {
        Category food = group(1L, "Food & Drink");
        Category meat = leaf(11L, "Meat", food.getId());
        Category sweets = leaf(12L, "Sweets", food.getId());

        Expense expense = expense(100L, food.getId(), "47.00", "EUR");
        List<ExpenseLine> lines = List.of(
                line(100L, "Meat", "20.00", meat.getId()),
                line(100L, "Sweets", "30.00", sweets.getId())
        );

        CategoryBreakdownCalculator.Result result = CategoryBreakdownCalculator.compute(
                List.of(expense),
                Map.of(100L, lines),
                Map.of(food.getId(), food, meat.getId(), meat, sweets.getId(), sweets)
        );

        assertTrue(result.byLeafCategory().stream().noneMatch(LineCategoryTotalResponse::unallocated));
        CategoryTotalResponse foodParent = findParent(result, "Food & Drink");
        assertEquals(0, new BigDecimal("50.00").compareTo(foodParent.totalAmount()));
    }

    @Test
    void expenseWithoutLines_putsFullTotalOnHeaderGroupOnly() {
        Category food = group(1L, "Food & Drink");
        Expense expense = expense(100L, food.getId(), "12.50", "EUR");

        CategoryBreakdownCalculator.Result result = CategoryBreakdownCalculator.compute(
                List.of(expense),
                Map.of(),
                Map.of(food.getId(), food)
        );

        assertTrue(result.byLeafCategory().isEmpty());
        assertEquals(1, result.byParentCategory().size());
        assertEquals(0, new BigDecimal("12.50").compareTo(result.byParentCategory().get(0).totalAmount()));
        assertEquals("Food & Drink", result.byParentCategory().get(0).categoryName());
    }

    @Test
    void lineWithChildlessTopLevelCategory_parentOnlyNoPieLeaf() {
        Category food = group(1L, "Food & Drink");
        Category meat = leaf(11L, "Meat", food.getId());
        Category shopping = group(3L, "Shopping");
        Expense expense = expense(100L, food.getId(), "10.00", "EUR");
        List<ExpenseLine> lines = List.of(line(100L, "Gift", "10.00", shopping.getId()));

        CategoryBreakdownCalculator.Result result = CategoryBreakdownCalculator.compute(
                List.of(expense),
                Map.of(100L, lines),
                Map.of(food.getId(), food, meat.getId(), meat, shopping.getId(), shopping)
        );

        // Childless group has nothing to split — no leaf row for the pie.
        assertTrue(result.byLeafCategory().isEmpty());
        assertEquals(1, result.byParentCategory().size());
        assertEquals("Shopping", result.byParentCategory().get(0).categoryName());
        assertEquals(0, new BigDecimal("10.00").compareTo(result.byParentCategory().get(0).totalAmount()));
    }

    @Test
    void lineWithParentSlugWhenGroupHasLeaves_becomesUnallocated() {
        Category food = group(1L, "Food & Drink");
        Category meat = leaf(11L, "Meat", food.getId());
        Category dairy = leaf(12L, "Dairy", food.getId());

        Expense expense = expense(100L, food.getId(), "10.00", "EUR");
        List<ExpenseLine> lines = List.of(
                line(100L, "Minced meat", "5.00", meat.getId()),
                line(100L, "Milk", "4.00", dairy.getId()),
                // OCR often returns parent slug "food" — must not become a Food slice.
                line(100L, "Mystery", "1.00", food.getId())
        );

        CategoryBreakdownCalculator.Result result = CategoryBreakdownCalculator.compute(
                List.of(expense),
                Map.of(100L, lines),
                Map.of(food.getId(), food, meat.getId(), meat, dairy.getId(), dairy)
        );

        assertTrue(result.byLeafCategory().stream()
                .noneMatch(row -> "Food & Drink".equals(row.categoryName()) && !row.unallocated()));
        assertEquals(0, new BigDecimal("5.00").compareTo(findLeaf(result, "Meat").totalAmount()));
        assertEquals(0, new BigDecimal("4.00").compareTo(findLeaf(result, "Dairy").totalAmount()));
        assertEquals(0, new BigDecimal("1.00").compareTo(findUnallocated(result, food.getId()).totalAmount()));
        assertEquals(0, new BigDecimal("10.00").compareTo(findParent(result, "Food & Drink").totalAmount()));
    }

    private static LineCategoryTotalResponse findLeaf(CategoryBreakdownCalculator.Result result, String name) {
        return result.byLeafCategory().stream()
                .filter(row -> name.equals(row.categoryName()) && !row.unallocated())
                .findFirst()
                .orElseThrow();
    }

    private static LineCategoryTotalResponse findUnallocated(
            CategoryBreakdownCalculator.Result result,
            Long parentId
    ) {
        return result.byLeafCategory().stream()
                .filter(row -> row.unallocated() && parentId.equals(row.parentId()))
                .findFirst()
                .orElseThrow();
    }

    private static CategoryTotalResponse findParent(CategoryBreakdownCalculator.Result result, String name) {
        return result.byParentCategory().stream()
                .filter(row -> name.equals(row.categoryName()))
                .findFirst()
                .orElseThrow();
    }

    private static Category group(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSlug(name.toLowerCase().replace(' ', '-').replace('&', 'a'));
        category.setParentId(null);
        category.setActive(true);
        return category;
    }

    private static Category leaf(Long id, String name, Long parentId) {
        Category category = group(id, name);
        category.setParentId(parentId);
        return category;
    }

    private static Expense expense(Long id, Long categoryId, String total, String currency) {
        Expense expense = new Expense();
        expense.setId(id);
        expense.setDocumentId(id);
        expense.setCategoryId(categoryId);
        expense.setExpenseDate(LocalDate.of(2024, 6, 15));
        expense.setTotalAmount(new BigDecimal(total));
        expense.setCurrency(currency);
        return expense;
    }

    private static ExpenseLine line(Long expenseId, String description, String amount, Long categoryId) {
        ExpenseLine line = new ExpenseLine();
        line.setExpenseId(expenseId);
        line.setDescription(description);
        line.setAmount(new BigDecimal(amount));
        line.setCategoryId(categoryId);
        line.setPosition(0);
        return line;
    }
}

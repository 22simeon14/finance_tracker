package com.financetracker.dashboard;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.expense.Expense;
import com.financetracker.expense.ExpenseLine;
import com.financetracker.expense.ExpenseLineRepository;
import com.financetracker.expense.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main Responsibility: Build owner-scoped dashboard aggregates from expenses.
 *
 * Reads ExpenseRepository SUM queries for currency / merchant.
 * Line breakdown (byLeafCategory / byParentCategory) loads expenses + lines and
 * uses CategoryBreakdownCalculator — never document_extractions.
 * Optional from/to dates are inclusive on expense_date (same as expense list).
 */
@Service
public class DashboardService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseLineRepository expenseLineRepository;
    private final CategoryRepository categoryRepository;

    public DashboardService(
            ExpenseRepository expenseRepository,
            ExpenseLineRepository expenseLineRepository,
            CategoryRepository categoryRepository
    ) {
        this.expenseRepository = expenseRepository;
        this.expenseLineRepository = expenseLineRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Return totals by currency, line breakdown, and merchant.
     * Empty expense set → empty lists (not null).
     * Date filters use boolean flags + sentinel dates (same as expense list).
     */
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId, LocalDate from, LocalDate to) {
        boolean hasFromDate = from != null;
        boolean hasToDate = to != null;
        LocalDate fromDate = hasFromDate ? from : LocalDate.EPOCH;
        LocalDate toDate = hasToDate ? to : LocalDate.EPOCH;

        List<CurrencyTotalResponse> totalsByCurrency = expenseRepository
                .sumTotalsByCurrency(userId, hasFromDate, fromDate, hasToDate, toDate)
                .stream()
                .map(row -> new CurrencyTotalResponse(
                        (String) row[0],
                        toBigDecimal(row[1])
                ))
                .toList();

        CategoryBreakdownCalculator.Result lineBreakdown = buildLineBreakdown(
                userId,
                hasFromDate,
                fromDate,
                hasToDate,
                toDate
        );

        List<MerchantTotalResponse> byMerchant = expenseRepository
                .sumByMerchant(userId, hasFromDate, fromDate, hasToDate, toDate)
                .stream()
                .map(row -> new MerchantTotalResponse(
                        (String) row[0],
                        (String) row[1],
                        toBigDecimal(row[2])
                ))
                .toList();

        return new DashboardResponse(
                totalsByCurrency,
                lineBreakdown.byLeafCategory(),
                lineBreakdown.byParentCategory(),
                byMerchant
        );
    }

    /**
     * Load period expenses + their lines and run the leaf/parent calculator.
     * Categories include inactive rows so names still resolve after deactivation.
     */
    private CategoryBreakdownCalculator.Result buildLineBreakdown(
            Long userId,
            boolean hasFromDate,
            LocalDate fromDate,
            boolean hasToDate,
            LocalDate toDate
    ) {
        List<Expense> expenses = expenseRepository.findAllByUserIdFiltered(
                userId,
                hasFromDate,
                fromDate,
                hasToDate,
                toDate,
                false,
                0L,
                false,
                ""
        );

        if (expenses.isEmpty()) {
            return new CategoryBreakdownCalculator.Result(List.of(), List.of());
        }

        List<Long> expenseIds = expenses.stream().map(Expense::getId).toList();
        Map<Long, List<ExpenseLine>> linesByExpenseId = expenseLineRepository
                .findByExpenseIdIn(expenseIds)
                .stream()
                .collect(Collectors.groupingBy(ExpenseLine::getExpenseId));

        Map<Long, Category> categoriesById = new HashMap<>();
        for (Category category : categoryRepository.findAll()) {
            categoriesById.put(category.getId(), category);
        }

        return CategoryBreakdownCalculator.compute(expenses, linesByExpenseId, categoriesById);
    }

    /**
     * JPQL SUM may return BigDecimal or another Number depending on the dialect.
     * Normalize to BigDecimal so JSON amounts stay precise.
     */
    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }
}

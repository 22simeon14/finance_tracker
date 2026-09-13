package com.financetracker.dashboard;

import com.financetracker.expense.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Main Responsibility: Build owner-scoped dashboard aggregates from expenses.
 *
 * Reads ExpenseRepository SUM queries only — never document_extractions.
 * Optional from/to dates are inclusive on expense_date (same as expense list).
 */
@Service
public class DashboardService {

    private final ExpenseRepository expenseRepository;

    public DashboardService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    /**
     * Return totals by currency, category, and merchant for this user.
     * Empty expense set → three empty lists (not null).
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

        List<CategoryTotalResponse> byCategory = expenseRepository
                .sumByCategory(userId, hasFromDate, fromDate, hasToDate, toDate)
                .stream()
                .map(row -> new CategoryTotalResponse(
                        toLong(row[0]),
                        (String) row[1],
                        (String) row[2],
                        toBigDecimal(row[3])
                ))
                .toList();

        List<MerchantTotalResponse> byMerchant = expenseRepository
                .sumByMerchant(userId, hasFromDate, fromDate, hasToDate, toDate)
                .stream()
                .map(row -> new MerchantTotalResponse(
                        (String) row[0],
                        (String) row[1],
                        toBigDecimal(row[2])
                ))
                .toList();

        return new DashboardResponse(totalsByCurrency, byCategory, byMerchant);
    }

    /** Aggregate id columns may arrive as Long or another Number. */
    private static Long toLong(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Expected numeric category id, got: " + value);
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

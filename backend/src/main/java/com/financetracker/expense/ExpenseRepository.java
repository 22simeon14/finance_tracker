package com.financetracker.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Main Responsibility: Database access for Expense entities.
 *
 * Exists-by-document helps catch double-approve races alongside status checks.
 * List/get/dashboard join documents so only the owning user's expenses are returned.
 * Optional filters use boolean flags (not null-checked binds) so PostgreSQL
 * never mis-types unused date/string/long parameters.
 * Dashboard SUM queries read expenses only (never extractions).
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    boolean existsByDocumentId(Long documentId);

    /**
     * Owner-scoped filtered list. Boolean flags turn filters on/off;
     * unused value params are still non-null sentinels from the service.
     * Newest expense_date first, then highest id for same-day ties.
     */
    @Query("""
            SELECT e FROM Expense e, Document d
            WHERE e.documentId = d.id AND d.userId = :userId
              AND (:hasFromDate = false OR e.expenseDate >= :fromDate)
              AND (:hasToDate = false OR e.expenseDate <= :toDate)
              AND (:hasCategoryId = false OR e.categoryId = :categoryId)
              AND (:hasMerchant = false
                   OR LOWER(COALESCE(e.merchant, '')) LIKE LOWER(CONCAT('%', :merchant, '%')))
            ORDER BY e.expenseDate DESC, e.id DESC
            """)
    List<Expense> findAllByUserIdFiltered(
            @Param("userId") Long userId,
            @Param("hasFromDate") boolean hasFromDate,
            @Param("fromDate") LocalDate fromDate,
            @Param("hasToDate") boolean hasToDate,
            @Param("toDate") LocalDate toDate,
            @Param("hasCategoryId") boolean hasCategoryId,
            @Param("categoryId") Long categoryId,
            @Param("hasMerchant") boolean hasMerchant,
            @Param("merchant") String merchant
    );

    /**
     * Owner-scoped single lookup. Missing or foreign expense both yield empty
     * so the service can map that to 404 without leaking existence.
     */
    @Query("""
            SELECT e FROM Expense e, Document d
            WHERE e.id = :id AND e.documentId = d.id AND d.userId = :userId
            """)
    Optional<Expense> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Sum amounts grouped by currency for the dashboard.
     * Rows are Object[]: [currency String, totalAmount Number].
     * Optional from/to use the same boolean-flag pattern as the list query.
     */
    @Query("""
            SELECT e.currency, SUM(e.totalAmount)
            FROM Expense e, Document d
            WHERE e.documentId = d.id AND d.userId = :userId
              AND (:hasFromDate = false OR e.expenseDate >= :fromDate)
              AND (:hasToDate = false OR e.expenseDate <= :toDate)
            GROUP BY e.currency
            ORDER BY e.currency ASC
            """)
    List<Object[]> sumTotalsByCurrency(
            @Param("userId") Long userId,
            @Param("hasFromDate") boolean hasFromDate,
            @Param("fromDate") LocalDate fromDate,
            @Param("hasToDate") boolean hasToDate,
            @Param("toDate") LocalDate toDate
    );

    /**
     * Sum amounts grouped by category and currency.
     * Rows are Object[]: [categoryId Long, categoryName String, currency String, totalAmount Number].
     * Joins Category so inactive categories still show their name.
     * Highest total first; name/currency break ties.
     */
    @Query("""
            SELECT e.categoryId, c.name, e.currency, SUM(e.totalAmount)
            FROM Expense e, Document d, Category c
            WHERE e.documentId = d.id AND d.userId = :userId
              AND e.categoryId = c.id
              AND (:hasFromDate = false OR e.expenseDate >= :fromDate)
              AND (:hasToDate = false OR e.expenseDate <= :toDate)
            GROUP BY e.categoryId, c.name, e.currency
            ORDER BY SUM(e.totalAmount) DESC, c.name ASC, e.currency ASC
            """)
    List<Object[]> sumByCategory(
            @Param("userId") Long userId,
            @Param("hasFromDate") boolean hasFromDate,
            @Param("fromDate") LocalDate fromDate,
            @Param("hasToDate") boolean hasToDate,
            @Param("toDate") LocalDate toDate
    );

    /**
     * Sum amounts grouped by merchant and currency.
     * Null or blank merchant is one bucket labeled '(none)'.
     * Rows are Object[]: [merchant String, currency String, totalAmount Number].
     */
    @Query("""
            SELECT CASE
                     WHEN e.merchant IS NULL OR TRIM(e.merchant) = '' THEN '(none)'
                     ELSE e.merchant
                   END,
                   e.currency,
                   SUM(e.totalAmount)
            FROM Expense e, Document d
            WHERE e.documentId = d.id AND d.userId = :userId
              AND (:hasFromDate = false OR e.expenseDate >= :fromDate)
              AND (:hasToDate = false OR e.expenseDate <= :toDate)
            GROUP BY CASE
                       WHEN e.merchant IS NULL OR TRIM(e.merchant) = '' THEN '(none)'
                       ELSE e.merchant
                     END,
                     e.currency
            ORDER BY
              CASE
                WHEN e.merchant IS NULL OR TRIM(e.merchant) = '' THEN '(none)'
                ELSE e.merchant
              END ASC,
              e.currency ASC
            """)
    List<Object[]> sumByMerchant(
            @Param("userId") Long userId,
            @Param("hasFromDate") boolean hasFromDate,
            @Param("fromDate") LocalDate fromDate,
            @Param("hasToDate") boolean hasToDate,
            @Param("toDate") LocalDate toDate
    );
}

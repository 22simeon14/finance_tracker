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
 * List/get join documents so only the owning user's expenses are returned.
 * Optional list filters (from/to/category/merchant) are AND-combined in JPQL.
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    boolean existsByDocumentId(Long documentId);

    /**
     * Owner-scoped filtered list. Null filter params mean "no constraint".
     * Newest expense_date first, then highest id for same-day ties.
     */
    @Query("""
            SELECT e FROM Expense e, Document d
            WHERE e.documentId = d.id AND d.userId = :userId
              AND (:fromDate IS NULL OR e.expenseDate >= :fromDate)
              AND (:toDate IS NULL OR e.expenseDate <= :toDate)
              AND (:categoryId IS NULL OR e.categoryId = :categoryId)
              AND (:merchant IS NULL OR LOWER(e.merchant) LIKE LOWER(CONCAT('%', :merchant, '%')))
            ORDER BY e.expenseDate DESC, e.id DESC
            """)
    List<Expense> findAllByUserIdFiltered(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("categoryId") Long categoryId,
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
}

package com.financetracker.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Main Responsibility: Database access for Expense entities.
 *
 * Exists-by-document helps catch double-approve races alongside status checks.
 * List/get join documents so only the owning user's expenses are returned.
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    boolean existsByDocumentId(Long documentId);

    /**
     * Owner-scoped list: expense belongs to a document owned by userId.
     * Newest expense_date first, then highest id for same-day ties.
     */
    @Query("""
            SELECT e FROM Expense e, Document d
            WHERE e.documentId = d.id AND d.userId = :userId
            ORDER BY e.expenseDate DESC, e.id DESC
            """)
    List<Expense> findAllByUserIdOrderByExpenseDateDescIdDesc(@Param("userId") Long userId);

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

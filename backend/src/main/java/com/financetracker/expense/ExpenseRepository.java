package com.financetracker.expense;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Main Responsibility: Database access for Expense entities.
 *
 * Exists-by-document helps catch double-approve races alongside status checks.
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    boolean existsByDocumentId(Long documentId);
}

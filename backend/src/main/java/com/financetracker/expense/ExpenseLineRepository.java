package com.financetracker.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Main Responsibility: Database access for ExpenseLine entities.
 *
 * findByExpenseIdOrderByPositionAsc loads confirmed lines in approve order.
 * Unapprove does not call delete here: expense_lines cascade from expenses.
 */
public interface ExpenseLineRepository extends JpaRepository<ExpenseLine, Long> {

    List<ExpenseLine> findByExpenseIdOrderByPositionAsc(Long expenseId);
}

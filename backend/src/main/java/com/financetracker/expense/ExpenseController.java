package com.financetracker.expense;

import com.financetracker.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Main Responsibility: Expose authenticated expense list and detail APIs.
 *
 * User id always comes from JWT via CurrentUser, never from the request body.
 * Ownership is enforced in ExpenseService (join through documents.user_id).
 */
@RestController
@RequestMapping("/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final CurrentUser currentUser;

    public ExpenseController(ExpenseService expenseService, CurrentUser currentUser) {
        this.expenseService = expenseService;
        this.currentUser = currentUser;
    }

    /** Return all expenses owned by the current user, newest first. */
    @GetMapping
    public List<ExpenseViewResponse> list() {
        return expenseService.list(currentUser.getUserId());
    }

    /**
     * Return one owned expense. Missing or foreign ids both resolve as 404.
     */
    @GetMapping("/{id}")
    public ExpenseViewResponse getById(@PathVariable Long id) {
        return expenseService.getById(currentUser.getUserId(), id);
    }
}

package com.financetracker.expense;

import com.financetracker.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Main Responsibility: Expose authenticated expense list, detail, edit, and unapprove APIs.
 *
 * User id always comes from JWT via CurrentUser, never from the request body.
 * Ownership is enforced in ExpenseService (join through documents.user_id).
 * Optional list query params are AND-combined; DELETE means unapprove (not forever wipe).
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

    /**
     * Return owned expenses, newest first. Optional filters: from/to (inclusive
     * expense_date), categoryId, merchant (case-insensitive contains).
     */
    @GetMapping
    public List<ExpenseViewResponse> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String merchant
    ) {
        return expenseService.list(currentUser.getUserId(), from, to, categoryId, merchant);
    }

    /**
     * Return one owned expense. Missing or foreign ids both resolve as 404.
     */
    @GetMapping("/{id}")
    public ExpenseViewResponse getById(@PathVariable Long id) {
        return expenseService.getById(currentUser.getUserId(), id);
    }

    /**
     * Update confirmed fields on an owned expense. Same validation as approve.
     * Missing/foreign → 404; inactive category / bad body → 400.
     */
    @PutMapping("/{id}")
    public ExpenseViewResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseWriteRequest request
    ) {
        return expenseService.update(currentUser.getUserId(), id, request);
    }

    /**
     * Unapprove: remove the expense and return the document to REVIEW_REQUIRED.
     * File is kept. Missing/foreign → 404. Response 204 with no body.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unapprove(@PathVariable Long id) {
        expenseService.unapprove(currentUser.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}

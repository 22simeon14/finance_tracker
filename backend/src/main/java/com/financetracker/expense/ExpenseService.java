package com.financetracker.expense;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.document.Document;
import com.financetracker.document.DocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Main Responsibility: Atomically create an expense from a reviewed document.
 *
 * Approve inserts expenses and sets documents.status = SAVED in one transaction.
 * Extraction proposals are left unchanged (history only; money truth is expenses).
 */
@Service
public class ExpenseService {

    private static final String STATUS_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    private static final String STATUS_SAVED = "SAVED";

    private final DocumentRepository documentRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    public ExpenseService(
            DocumentRepository documentRepository,
            CategoryRepository categoryRepository,
            ExpenseRepository expenseRepository
    ) {
        this.documentRepository = documentRepository;
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
    }

    /**
     * Confirm review fields into a new expense and mark the document SAVED.
     * Wrong owner / missing → 404; wrong status → 409; inactive/missing category → 400.
     * Any failure rolls back so the document stays REVIEW_REQUIRED with no expense row.
     */
    @Transactional
    public ExpenseResponse approve(Long userId, Long documentId, ApproveDocumentRequest request) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        if (!STATUS_REVIEW_REQUIRED.equals(document.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Document can only be approved from REVIEW_REQUIRED"
            );
        }

        Category category = categoryRepository.findByIdAndIsActiveTrue(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Category must exist and be active"
                ));

        Expense expense = new Expense();
        expense.setDocumentId(document.getId());
        expense.setCategoryId(category.getId());
        expense.setMerchant(normalizeMerchant(request.merchant()));
        expense.setExpenseDate(request.expenseDate());
        expense.setTotalAmount(request.totalAmount());
        expense.setCurrency(request.currency());

        Expense savedExpense = expenseRepository.save(expense);

        document.setStatus(STATUS_SAVED);
        documentRepository.save(document);

        return toResponse(savedExpense);
    }

    /** Treat blank merchant as null so the DB stores a clean optional value. */
    private static String normalizeMerchant(String merchant) {
        if (merchant == null || merchant.isBlank()) {
            return null;
        }
        return merchant.trim();
    }

    private ExpenseResponse toResponse(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getDocumentId(),
                expense.getCategoryId(),
                expense.getMerchant(),
                expense.getExpenseDate(),
                expense.getTotalAmount(),
                expense.getCurrency(),
                expense.getCreatedAt()
        );
    }
}

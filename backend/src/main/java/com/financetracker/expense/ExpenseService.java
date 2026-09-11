package com.financetracker.expense;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.document.Document;
import com.financetracker.document.DocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Main Responsibility: Create expenses from review approve, and read owner-scoped lists/details.
 *
 * Approve inserts expenses and sets documents.status = SAVED in one transaction.
 * Extraction proposals are left unchanged (history only; money truth is expenses).
 * List/get join through documents.user_id so foreign expenses never leak (404).
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

    /** Return all expenses for this user, newest expense_date first. */
    @Transactional(readOnly = true)
    public List<ExpenseViewResponse> list(Long userId) {
        return expenseRepository.findAllByUserIdOrderByExpenseDateDescIdDesc(userId).stream()
                .map(expense -> toViewResponse(userId, expense))
                .toList();
    }

    /**
     * Return one owned expense. Missing or foreign id → 404 (same as documents).
     */
    @Transactional(readOnly = true)
    public ExpenseViewResponse getById(Long userId, Long expenseId) {
        Expense expense = expenseRepository.findByIdAndUserId(expenseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        return toViewResponse(userId, expense);
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

    /**
     * Build the public read DTO: resolve category name (even if later inactive)
     * and document filename via an owner-scoped document lookup.
     */
    private ExpenseViewResponse toViewResponse(Long userId, Expense expense) {
        // findById (not findByIdAndIsActiveTrue) so inactive categories still show a name.
        String categoryName = categoryRepository.findById(expense.getCategoryId())
                .map(Category::getName)
                .orElse(null);

        String originalFilename = documentRepository.findByIdAndUserId(expense.getDocumentId(), userId)
                .map(Document::getOriginalFilename)
                .orElse(null);

        String documentFileUrl = "/documents/" + expense.getDocumentId() + "/file";

        return new ExpenseViewResponse(
                expense.getId(),
                expense.getDocumentId(),
                expense.getCategoryId(),
                categoryName,
                expense.getMerchant(),
                expense.getExpenseDate(),
                expense.getTotalAmount(),
                expense.getCurrency(),
                expense.getCreatedAt(),
                documentFileUrl,
                originalFilename
        );
    }
}

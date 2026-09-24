package com.financetracker.document.extraction;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Main Responsibility: Sanitize LLM header and line-item proposals with simple Java rules.
 *
 * Forces EUR, drops non-positive amounts and absurd dates, and nulls category ids
 * that are not in the active list. Drops line items without a description or with
 * amount ≤ 0. Does not invent totals with regex. Empty line list after cleanup is fine.
 */
@Component
public class ExtractionValidator {

    private static final String EUR = "EUR";
    /** Receipts older than this are treated as OCR/LLM noise. */
    private static final LocalDate EARLIEST_REASONABLE_DATE = LocalDate.of(1990, 1, 1);

    /**
     * Return a cleaned copy. Currency is always EUR; invalid line items are removed.
     */
    public ExtractionResult validate(ExtractionResult parsed, List<CategoryOption> categories) {
        Set<Long> activeCategoryIds = toActiveIds(categories);

        String merchant = normalizeMerchant(parsed.merchant());
        LocalDate date = normalizeDate(parsed.date());
        BigDecimal amount = normalizeAmount(parsed.totalAmount());
        Long categoryId = normalizeCategoryId(parsed.categoryId(), activeCategoryIds);
        List<LineItemProposal> lineItems = normalizeLineItems(parsed.lineItems(), activeCategoryIds);

        return new ExtractionResult(
                parsed.rawOcrText(),
                merchant,
                date,
                amount,
                EUR,
                categoryId,
                lineItems
        );
    }

    private static Set<Long> toActiveIds(List<CategoryOption> categories) {
        Set<Long> ids = new HashSet<>();
        if (categories == null) {
            return ids;
        }
        for (CategoryOption option : categories) {
            if (option != null && option.id() != null) {
                ids.add(option.id());
            }
        }
        return ids;
    }

    /**
     * Keep rows with a non-blank description and a positive amount.
     * Unknown categoryId becomes null; the row itself is kept.
     */
    private static List<LineItemProposal> normalizeLineItems(
            List<LineItemProposal> lineItems,
            Set<Long> activeCategoryIds
    ) {
        if (lineItems == null || lineItems.isEmpty()) {
            return List.of();
        }
        List<LineItemProposal> cleaned = new ArrayList<>();
        for (LineItemProposal item : lineItems) {
            if (item == null) {
                continue;
            }
            String description = normalizeMerchant(item.description());
            BigDecimal amount = normalizeAmount(item.amount());
            if (description == null || amount == null) {
                continue;
            }
            Long categoryId = normalizeCategoryId(item.categoryId(), activeCategoryIds);
            cleaned.add(new LineItemProposal(description, amount, categoryId));
        }
        return List.copyOf(cleaned);
    }

    private static String normalizeMerchant(String merchant) {
        if (merchant == null || merchant.isBlank()) {
            return null;
        }
        return merchant.trim();
    }

    /**
     * Keep null; reject zero/negative. Does not invent a total from raw text.
     */
    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return amount;
    }

    /**
     * Drop dates far in the past or more than one day ahead of today.
     */
    private static LocalDate normalizeDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate latestAllowed = LocalDate.now().plusDays(1);
        if (date.isBefore(EARLIEST_REASONABLE_DATE) || date.isAfter(latestAllowed)) {
            return null;
        }
        return date;
    }

    private static Long normalizeCategoryId(Long categoryId, Set<Long> activeCategoryIds) {
        if (categoryId == null) {
            return null;
        }
        if (!activeCategoryIds.contains(categoryId)) {
            return null;
        }
        return categoryId;
    }
}

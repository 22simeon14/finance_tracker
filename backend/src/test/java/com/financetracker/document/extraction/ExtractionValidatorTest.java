package com.financetracker.document.extraction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Main Responsibility: Verify ExtractionValidator EUR / amount / date / category rules.
 */
class ExtractionValidatorTest {

    private static final List<CategoryOption> CATEGORIES = List.of(
            new CategoryOption(1L, "Food & Drink", "food"),
            new CategoryOption(2L, "Transport", "transport")
    );

    private final ExtractionValidator validator = new ExtractionValidator();

    @Test
    void forcesEurAndKeepsValidHeaders() {
        ExtractionResult parsed = ExtractionResult.ofHeaders(
                "raw",
                "  Cafe  ",
                LocalDate.of(2024, 3, 10),
                new BigDecimal("12.50"),
                "USD",
                1L
        );

        ExtractionResult result = validator.validate(parsed, CATEGORIES);

        assertEquals("raw", result.rawOcrText());
        assertEquals("Cafe", result.merchant());
        assertEquals(LocalDate.of(2024, 3, 10), result.date());
        assertEquals(0, new BigDecimal("12.50").compareTo(result.totalAmount()));
        assertEquals("EUR", result.currency());
        assertEquals(1L, result.categoryId());
        assertTrue(result.lineItems().isEmpty());
    }

    @Test
    void dropsNonPositiveAmount() {
        ExtractionResult parsed = ExtractionResult.ofHeaders(
                "raw",
                "Shop",
                LocalDate.of(2024, 1, 1),
                BigDecimal.ZERO,
                "EUR",
                null
        );

        ExtractionResult result = validator.validate(parsed, CATEGORIES);

        assertNull(result.totalAmount());
        assertEquals("EUR", result.currency());
    }

    @Test
    void dropsAbsurdDates() {
        ExtractionResult tooOld = ExtractionResult.ofHeaders(
                "raw", null, LocalDate.of(1980, 1, 1), null, "EUR", null
        );
        ExtractionResult tooFuture = ExtractionResult.ofHeaders(
                "raw", null, LocalDate.now().plusDays(10), null, "EUR", null
        );

        assertNull(validator.validate(tooOld, CATEGORIES).date());
        assertNull(validator.validate(tooFuture, CATEGORIES).date());
    }

    @Test
    void nullsUnknownCategoryId() {
        ExtractionResult parsed = ExtractionResult.ofHeaders(
                "raw",
                "Shop",
                LocalDate.of(2024, 5, 1),
                new BigDecimal("5.00"),
                "EUR",
                999L
        );

        ExtractionResult result = validator.validate(parsed, CATEGORIES);

        assertNull(result.categoryId());
        assertEquals("Shop", result.merchant());
    }

    @Test
    void blankMerchantBecomesNull() {
        ExtractionResult parsed = ExtractionResult.ofHeaders(
                "raw", "   ", null, null, "BGN", null
        );

        ExtractionResult result = validator.validate(parsed, CATEGORIES);

        assertNull(result.merchant());
        assertEquals("EUR", result.currency());
    }
}

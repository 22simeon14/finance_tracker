package com.financetracker.document.extraction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Main Responsibility: Verify ExtractionValidator EUR / amount / date / category / lines.
 */
class ExtractionValidatorTest {

    private static final List<CategoryOption> CATEGORIES = List.of(
            new CategoryOption(1L, "Food & Drink", "food"),
            new CategoryOption(2L, "Transport", "transport"),
            new CategoryOption(11L, "Meat", "meat")
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

    @Test
    void dropsLineItemsWithoutDescriptionOrNonPositiveAmount() {
        ExtractionResult parsed = new ExtractionResult(
                "raw",
                "Lidl",
                LocalDate.of(2024, 6, 1),
                new BigDecimal("47"),
                "EUR",
                1L,
                List.of(
                        LineItemProposal.of("Meat", new BigDecimal("8"), 11L),
                        LineItemProposal.of("  ", new BigDecimal("3"), 1L),
                        LineItemProposal.of("Soap", BigDecimal.ZERO, 1L),
                        LineItemProposal.of(null, new BigDecimal("2"), 1L),
                        LineItemProposal.of("Bread", new BigDecimal("-1"), 1L)
                )
        );

        ExtractionResult result = validator.validate(parsed, CATEGORIES);

        assertEquals(1, result.lineItems().size());
        assertEquals("Meat", result.lineItems().get(0).description());
        assertEquals(11L, result.lineItems().get(0).categoryId());
    }

    @Test
    void keepsLineWithNullCategoryWhenSlugWasUnknown() {
        ExtractionResult parsed = new ExtractionResult(
                "raw",
                "Shop",
                null,
                new BigDecimal("5"),
                "EUR",
                null,
                List.of(LineItemProposal.of("Mystery", new BigDecimal("5"), null))
        );

        ExtractionResult result = validator.validate(parsed, CATEGORIES);

        assertEquals(1, result.lineItems().size());
        assertNull(result.lineItems().get(0).categoryId());
    }

    @Test
    void defaultsMissingQuantityAndUnitPriceToOneTimesAmount() {
        ExtractionResult parsed = new ExtractionResult(
                "raw",
                "Shop",
                null,
                new BigDecimal("4"),
                "EUR",
                null,
                List.of(LineItemProposal.of("Soap", new BigDecimal("4"), 21L))
        );

        ExtractionResult result = validator.validate(parsed, CATEGORIES);

        assertEquals(1, result.lineItems().size());
        assertEquals(0, BigDecimal.ONE.compareTo(result.lineItems().get(0).quantity()));
        assertEquals(0, new BigDecimal("4").compareTo(result.lineItems().get(0).unitPrice()));
        assertEquals(0, new BigDecimal("4").compareTo(result.lineItems().get(0).amount()));
    }

    @Test
    void nonPositiveQuantityAndUnitPriceBecomeOneTimesAmount() {
        ExtractionResult parsed = new ExtractionResult(
                "raw",
                "Shop",
                null,
                new BigDecimal("6"),
                "EUR",
                null,
                List.of(
                        new LineItemProposal(
                                "Waffle",
                                BigDecimal.ZERO,
                                new BigDecimal("-1"),
                                new BigDecimal("6"),
                                null
                        ),
                        new LineItemProposal(
                                "Coffee",
                                new BigDecimal("2"),
                                new BigDecimal("3"),
                                new BigDecimal("6"),
                                1L
                        )
                )
        );

        ExtractionResult result = validator.validate(parsed, CATEGORIES);

        assertEquals(2, result.lineItems().size());
        assertEquals(0, BigDecimal.ONE.compareTo(result.lineItems().get(0).quantity()));
        assertEquals(0, new BigDecimal("6").compareTo(result.lineItems().get(0).unitPrice()));
        assertEquals(0, new BigDecimal("2").compareTo(result.lineItems().get(1).quantity()));
        assertEquals(0, new BigDecimal("3").compareTo(result.lineItems().get(1).unitPrice()));
    }

    @Test
    void allowsEmptyLineItemsAfterCleanup() {
        ExtractionResult parsed = new ExtractionResult(
                "raw",
                "Shop",
                LocalDate.of(2024, 1, 1),
                new BigDecimal("10"),
                "EUR",
                1L,
                List.of(LineItemProposal.of("", new BigDecimal("10"), 1L))
        );

        ExtractionResult result = validator.validate(parsed, CATEGORIES);

        assertTrue(result.hasUsableHeader());
        assertTrue(result.lineItems().isEmpty());
    }
}

package com.financetracker.document.extraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Main Responsibility: Unit tests for digital PDF text usability heuristics.
 */
class PdfTextUsabilityTest {

    @Test
    void acceptsTextWithEnoughContent() {
        assertTrue(PdfTextUsability.isUsable("Invoice ACME Corp Total 123.45 EUR"));
    }

    @Test
    void rejectsBlankOrTooShortText() {
        assertFalse(PdfTextUsability.isUsable(null));
        assertFalse(PdfTextUsability.isUsable("   "));
        assertFalse(PdfTextUsability.isUsable("short"));
    }

    @Test
    void rejectsReplacementCharacterGarbage() {
        String garbage = "\uFFFD".repeat(20);
        assertFalse(PdfTextUsability.isUsable(garbage));
    }
}

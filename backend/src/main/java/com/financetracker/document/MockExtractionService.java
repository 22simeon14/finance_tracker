package com.financetracker.document;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Main Responsibility: Build deterministic mock OCR / proposed expense fields.
 *
 * No real OCR libraries — Step 10 uses fixed sample values so upload → review
 * can be tested end-to-end. proposedCategoryId is left null on purpose (partial).
 */
@Service
public class MockExtractionService {

    private static final String MOCK_RAW_OCR_TEXT =
            "MOCK OCR\nDemo Cafe\nTotal: 12.50 EUR\nThank you";
    private static final String MOCK_MERCHANT = "Demo Cafe";
    private static final BigDecimal MOCK_AMOUNT = new BigDecimal("12.50");
    private static final String MOCK_CURRENCY = "EUR";

    /**
     * Overwrite extraction fields with the fixed mock success payload.
     * Category stays null so the review form still needs one manual choice.
     */
    public void applyMockProposal(DocumentExtraction extraction) {
        extraction.setRawOcrText(MOCK_RAW_OCR_TEXT);
        extraction.setProposedMerchant(MOCK_MERCHANT);
        extraction.setProposedDate(LocalDate.now());
        extraction.setProposedAmount(MOCK_AMOUNT);
        extraction.setProposedCurrency(MOCK_CURRENCY);
        extraction.setProposedCategoryId(null);
    }

    /** Clear all proposed fields (manual-continue empty form / failed cleanup). */
    public void clearProposedFields(DocumentExtraction extraction) {
        extraction.setRawOcrText(null);
        extraction.setProposedMerchant(null);
        extraction.setProposedDate(null);
        extraction.setProposedAmount(null);
        extraction.setProposedCurrency(null);
        extraction.setProposedCategoryId(null);
    }
}

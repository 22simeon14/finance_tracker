package com.financetracker.document.extraction;

import java.util.List;

/**
 * Main Responsibility: Parse OCR text into ExtractionResult via an LLM.
 *
 * Always the semantic parser (not a rules fallback). Production bean:
 * GroqReceiptParser — Groq Cloud chat API (OpenAI-compatible HTTP shape; not OpenAI
 * and not xAI Grok). Implementations map category by slug against the given active
 * options (groups and leaves); unknown slug → null category. May include lineItems.
 */
public interface ReceiptParser {

    /**
     * Build header and optional line-item proposals from OCR text.
     * Throws ExtractionException when the model is unavailable or output is unusable.
     */
    ExtractionResult parse(OcrResult ocrResult, List<CategoryOption> categories);
}

package com.financetracker.document.extraction;

import java.util.List;

/**
 * Main Responsibility: Parse OCR text into header ExtractionResult via an LLM.
 *
 * Always the semantic parser (not a rules fallback). Production bean (next):
 * GroqReceiptParser — Groq Cloud chat API (OpenAI-compatible HTTP shape; not OpenAI
 * and not xAI Grok). Implementations map category by slug against the given active
 * options; unknown slug → null category.
 */
public interface ReceiptParser {

    /**
     * Build header proposals from OCR text. lineItems must stay empty for now.
     * Throws ExtractionException when the model is unavailable or output is unusable.
     */
    ExtractionResult parse(OcrResult ocrResult, List<CategoryOption> categories);
}

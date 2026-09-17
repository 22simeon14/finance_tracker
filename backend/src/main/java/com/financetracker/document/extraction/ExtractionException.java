package com.financetracker.document.extraction;

/**
 * Main Responsibility: Mark hard extraction failures (empty text, OCR/LLM down, bad parse).
 *
 * DocumentService catches this and sets PROCESSING_FAILED so the user can retry
 * or continue manually. Never used for partial-but-usable headers.
 */
public class ExtractionException extends RuntimeException {

    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}

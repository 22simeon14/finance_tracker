package com.financetracker.document.extraction;

import java.util.List;

/**
 * Main Responsibility: Text taken from a stored document (PDF layer or OCR sidecar).
 *
 * rawText is what the receipt parser reads. lines keep layout for a later step;
 * they are not written to the database in this milestone.
 */
public record OcrResult(String rawText, List<OcrLine> lines) {

    public OcrResult {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /** True when there is no non-blank text to send to the receipt parser. */
    public boolean isBlank() {
        return rawText == null || rawText.isBlank();
    }
}

package com.financetracker.document.extraction;

/**
 * Main Responsibility: One OCR text line plus an optional bounding box.
 *
 * Lines are kept in memory for later line-item work; they are not persisted yet.
 * Digital PDF text may omit the box (bbox null).
 */
public record OcrLine(String text, BoundingBox bbox) {
}

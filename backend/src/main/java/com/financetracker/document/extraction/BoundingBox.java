package com.financetracker.document.extraction;

/**
 * Main Responsibility: Optional axis-aligned box for one OCR line on the page image.
 *
 * Coordinates come from the OCR sidecar; digital PDF text layers usually leave this null.
 */
public record BoundingBox(double x0, double y0, double x1, double y1) {
}

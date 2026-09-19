package com.financetracker.document.extraction;

/**
 * Main Responsibility: HTTP client contract for the RapidOCR sidecar.
 *
 * Production bean: HttpOcrClient. The text gateway calls this for images and for
 * PDF pages that lack a usable digital text layer.
 */
public interface OcrClient {

    /**
     * Run OCR on one image (JPEG, PNG, or a rasterized PDF page).
     * Throws ExtractionException when the sidecar is unreachable or returns an error.
     */
    OcrResult recognize(byte[] imageBytes);
}

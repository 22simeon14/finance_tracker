package com.financetracker.document.extraction;

import java.nio.file.Path;

/**
 * Main Responsibility: Turn a stored PDF or image into OcrResult text (and lines).
 *
 * Routes by MIME: digital PDF text when usable, otherwise rasterize/OCR;
 * JPEG/PNG go straight to the OCR sidecar. Implemented in later pipeline steps.
 */
public interface DocumentTextGateway {

    /**
     * Read text from the file on disk. Throws ExtractionException when the
     * sidecar is down or the file cannot be read.
     */
    OcrResult extractText(Path storedFile, String mimeType);
}

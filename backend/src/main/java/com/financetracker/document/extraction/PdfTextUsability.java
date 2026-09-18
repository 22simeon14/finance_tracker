package com.financetracker.document.extraction;

/**
 * Main Responsibility: Decide whether PDFBox text-layer output is good enough to skip OCR.
 *
 * Scanned PDFs and broken encodings often yield very little text or many replacement chars.
 */
final class PdfTextUsability {

    /** Minimum non-whitespace characters before we trust the digital layer. */
    private static final int MIN_NON_WHITESPACE_CHARS = 15;

    /** Above this share of U+FFFD chars, treat the layer as garbage. */
    private static final double MAX_REPLACEMENT_CHAR_RATIO = 0.05;

    private PdfTextUsability() {
    }

    static boolean isUsable(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        int nonWhitespace = 0;
        int replacementChars = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\uFFFD') {
                replacementChars++;
            }
            if (!Character.isWhitespace(character)) {
                nonWhitespace++;
            }
        }

        if (nonWhitespace < MIN_NON_WHITESPACE_CHARS) {
            return false;
        }

        return ((double) replacementChars / text.length()) <= MAX_REPLACEMENT_CHAR_RATIO;
    }
}

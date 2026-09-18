package com.financetracker.document.extraction;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Responsibility: Combine per-page OCR results into one OcrResult for the parser.
 */
final class OcrResultMerger {

    private OcrResultMerger() {
    }

    static OcrResult merge(List<OcrResult> pageResults) {
        if (pageResults == null || pageResults.isEmpty()) {
            return new OcrResult("", List.of());
        }

        StringBuilder textBuilder = new StringBuilder();
        List<OcrLine> mergedLines = new ArrayList<>();

        for (OcrResult pageResult : pageResults) {
            if (pageResult == null) {
                continue;
            }
            String pageText = pageResult.rawText();
            if (pageText != null && !pageText.isBlank()) {
                if (!textBuilder.isEmpty()) {
                    textBuilder.append('\n');
                }
                textBuilder.append(pageText.strip());
            }
            mergedLines.addAll(pageResult.lines());
        }

        return new OcrResult(textBuilder.toString(), List.copyOf(mergedLines));
    }
}

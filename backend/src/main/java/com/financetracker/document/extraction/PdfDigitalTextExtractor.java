package com.financetracker.document.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Main Responsibility: Read the embedded text layer from a digital PDF via PDFBox.
 *
 * Does not rasterize or call OCR; the gateway decides whether this output is usable.
 */
@Component
class PdfDigitalTextExtractor {

    /**
     * Extract plain text and simple line splits (no bounding boxes for digital text).
     */
    OcrResult extract(Path pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(document);
            if (rawText == null) {
                return new OcrResult("", List.of());
            }
            return new OcrResult(rawText, toLines(rawText));
        } catch (IOException exception) {
            throw new ExtractionException("Failed to read PDF text layer", exception);
        }
    }

    private List<OcrLine> toLines(String rawText) {
        String[] parts = rawText.split("\\R");
        List<OcrLine> lines = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                // Digital PDF text has no image coordinates; bbox stays null.
                lines.add(new OcrLine(trimmed, null));
            }
        }
        return List.copyOf(lines);
    }
}

package com.financetracker.document.extraction;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Main Responsibility: Verify PDFBox reads text from a tiny generated digital PDF.
 */
class PdfDigitalTextExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsTextFromGeneratedPdf() throws Exception {
        Path pdfPath = tempDir.resolve("invoice.pdf");
        writeSamplePdf(pdfPath, "Merchant Test Shop Total 42.50 EUR");

        PdfDigitalTextExtractor extractor = new PdfDigitalTextExtractor();
        OcrResult result = extractor.extract(pdfPath);

        assertTrue(result.rawText().contains("Test Shop"));
        assertTrue(result.rawText().contains("42.50"));
        assertFalse(result.lines().isEmpty());
        assertTrue(PdfTextUsability.isUsable(result.rawText()));
    }

    private void writeSamplePdf(Path pdfPath, String body) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(50, 700);
                content.showText(body);
                content.endText();
            }
            document.save(pdfPath.toFile());
        }
    }
}

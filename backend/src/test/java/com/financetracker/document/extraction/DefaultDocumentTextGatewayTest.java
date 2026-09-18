package com.financetracker.document.extraction;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Main Responsibility: Verify PDF routing uses digital text or OCR fallback.
 */
class DefaultDocumentTextGatewayTest {

    @TempDir
    Path tempDir;

    @Test
    void digitalPdfUsesTextLayerWithoutOcr() throws Exception {
        Path pdfPath = tempDir.resolve("digital.pdf");
        writeTextPdf(pdfPath, "Store Alpha Amount due 99.99 EUR");

        OcrClient ocrClient = mock(OcrClient.class);
        DefaultDocumentTextGateway gateway = newGateway(ocrClient);

        OcrResult result = gateway.extractText(pdfPath, "application/pdf");

        assertTrue(result.rawText().contains("Store Alpha"));
        verify(ocrClient, never()).recognize(any());
    }

    @Test
    void scannedPdfFallsBackToOcr() throws Exception {
        Path pdfPath = tempDir.resolve("blank.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdfPath.toFile());
        }

        OcrClient ocrClient = mock(OcrClient.class);
        when(ocrClient.recognize(any())).thenReturn(
                new OcrResult("OCR merchant\nTotal 10.00", List.of(new OcrLine("OCR merchant", null)))
        );
        DefaultDocumentTextGateway gateway = newGateway(ocrClient);

        OcrResult result = gateway.extractText(pdfPath, "application/pdf");

        assertTrue(result.rawText().contains("OCR merchant"));
        verify(ocrClient).recognize(any());
    }

    @Test
    void imageMimeTypeUsesOcrClient() throws Exception {
        Path imagePath = tempDir.resolve("receipt.png");
        Files.write(imagePath, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        OcrClient ocrClient = mock(OcrClient.class);
        when(ocrClient.recognize(any())).thenReturn(new OcrResult("Receipt text", List.of()));
        DefaultDocumentTextGateway gateway = newGateway(ocrClient);

        OcrResult result = gateway.extractText(imagePath, "image/png");

        assertEquals("Receipt text", result.rawText());
        verify(ocrClient).recognize(any());
    }

    @Test
    void missingOcrClientFailsForImageUpload() throws Exception {
        Path imagePath = tempDir.resolve("photo.jpg");
        Files.write(imagePath, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        DefaultDocumentTextGateway gateway = new DefaultDocumentTextGateway(
                new PdfDigitalTextExtractor(),
                new PdfPageRasterizer(),
                unavailableOcrClient()
        );

        ExtractionException exception = assertThrows(
                ExtractionException.class,
                () -> gateway.extractText(imagePath, "image/jpeg")
        );
        assertTrue(exception.getMessage().contains("OCR client is not configured"));
    }

    private DefaultDocumentTextGateway newGateway(OcrClient ocrClient) {
        @SuppressWarnings("unchecked")
        ObjectProvider<OcrClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ocrClient);
        return new DefaultDocumentTextGateway(
                new PdfDigitalTextExtractor(),
                new PdfPageRasterizer(),
                provider
        );
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<OcrClient> unavailableOcrClient() {
        ObjectProvider<OcrClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    private void writeTextPdf(Path pdfPath, String body) throws Exception {
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

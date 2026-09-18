package com.financetracker.document.extraction;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Main Responsibility: Route stored files to PDFBox text or OCR by MIME type.
 *
 * Digital PDFs use the text layer when usable; scanned PDFs are rasterized then OCR'd.
 * JPEG/PNG go straight to the OCR sidecar via OcrClient.
 */
@Service
public class DefaultDocumentTextGateway implements DocumentTextGateway {

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png"
    );

    private final PdfDigitalTextExtractor pdfDigitalTextExtractor;
    private final PdfPageRasterizer pdfPageRasterizer;
    private final ObjectProvider<OcrClient> ocrClient;

    public DefaultDocumentTextGateway(
            PdfDigitalTextExtractor pdfDigitalTextExtractor,
            PdfPageRasterizer pdfPageRasterizer,
            ObjectProvider<OcrClient> ocrClient
    ) {
        this.pdfDigitalTextExtractor = pdfDigitalTextExtractor;
        this.pdfPageRasterizer = pdfPageRasterizer;
        this.ocrClient = ocrClient;
    }

    @Override
    public OcrResult extractText(Path storedFile, String mimeType) {
        if (mimeType == null) {
            throw new ExtractionException("Missing MIME type for stored file");
        }

        if ("application/pdf".equals(mimeType)) {
            return extractFromPdf(storedFile);
        }
        if (IMAGE_MIME_TYPES.contains(mimeType)) {
            return recognizeImageFile(storedFile);
        }

        throw new ExtractionException("Unsupported MIME type for text extraction: " + mimeType);
    }

    private OcrResult extractFromPdf(Path pdfFile) {
        OcrResult digitalText = pdfDigitalTextExtractor.extract(pdfFile);
        if (PdfTextUsability.isUsable(digitalText.rawText())) {
            return digitalText;
        }
        return ocrRasterizedPdf(pdfFile);
    }

    private OcrResult ocrRasterizedPdf(Path pdfFile) {
        OcrClient client = requireOcrClient();
        List<byte[]> pageImages = pdfPageRasterizer.rasterizePages(pdfFile);
        if (pageImages.isEmpty()) {
            return new OcrResult("", List.of());
        }

        List<OcrResult> pageResults = new ArrayList<>(pageImages.size());
        for (byte[] pageImage : pageImages) {
            pageResults.add(client.recognize(pageImage));
        }
        return OcrResultMerger.merge(pageResults);
    }

    private OcrResult recognizeImageFile(Path imageFile) {
        OcrClient client = requireOcrClient();
        try {
            return client.recognize(Files.readAllBytes(imageFile));
        } catch (IOException exception) {
            throw new ExtractionException("Failed to read image file for OCR", exception);
        }
    }

    private OcrClient requireOcrClient() {
        OcrClient client = ocrClient.getIfAvailable();
        if (client == null) {
            throw new ExtractionException("OCR client is not configured");
        }
        return client;
    }
}

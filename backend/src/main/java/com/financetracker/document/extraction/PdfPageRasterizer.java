package com.financetracker.document.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Main Responsibility: Render each PDF page to PNG bytes for the OCR sidecar.
 *
 * Used when the digital text layer is missing or unusable (scanned invoices).
 */
@Component
class PdfPageRasterizer {

    /** Balance OCR quality and payload size for the 5 MB upload cap. */
    private static final float RENDER_DPI = 200f;

    /**
     * One PNG byte array per page, in order.
     */
    List<byte[]> rasterizePages(Path pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            List<byte[]> pages = new ArrayList<>(pageCount);
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, RENDER_DPI, ImageType.RGB);
                pages.add(toPngBytes(image));
            }
            return List.copyOf(pages);
        } catch (IOException exception) {
            throw new ExtractionException("Failed to rasterize PDF pages", exception);
        }
    }

    private byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}

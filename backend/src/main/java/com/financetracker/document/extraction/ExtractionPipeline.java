package com.financetracker.document.extraction;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * Main Responsibility: Run text extract → LLM parse → validate for one stored file.
 *
 * Single entry point for DocumentService (and a future async worker). Collaborators:
 * DocumentTextGateway, OcrClient, ReceiptParser (GroqReceiptParser when GROQ_API_KEY
 * is set). Missing gateway or parser → ExtractionException (PROCESSING_FAILED).
 */
@Service
public class ExtractionPipeline {

    private final ObjectProvider<DocumentTextGateway> documentTextGateway;
    private final ObjectProvider<ReceiptParser> receiptParser;
    private final ExtractionValidator extractionValidator;
    private final CategoryRepository categoryRepository;

    public ExtractionPipeline(
            ObjectProvider<DocumentTextGateway> documentTextGateway,
            ObjectProvider<ReceiptParser> receiptParser,
            ExtractionValidator extractionValidator,
            CategoryRepository categoryRepository
    ) {
        this.documentTextGateway = documentTextGateway;
        this.receiptParser = receiptParser;
        this.extractionValidator = extractionValidator;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Extract header proposals from a file already on disk.
     * Empty OCR text → result with no usable header (caller marks PROCESSING_FAILED).
     * Infrastructure / parse errors → ExtractionException.
     */
    public ExtractionResult extract(Path storedFile, String mimeType) {
        DocumentTextGateway textGateway = documentTextGateway.getIfAvailable();
        ReceiptParser parser = receiptParser.getIfAvailable();
        if (textGateway == null || parser == null) {
            throw new ExtractionException(
                    "Extraction text gateway or receipt parser is not configured"
            );
        }

        OcrResult ocrResult = textGateway.extractText(storedFile, mimeType);
        if (ocrResult == null || ocrResult.isBlank()) {
            // No text to parse — return empty headers so the caller can fail processing.
            return ExtractionResult.ofHeaders(null, null, null, null, null, null);
        }

        List<CategoryOption> categories = loadActiveCategories();
        ExtractionResult parsed = parser.parse(ocrResult, categories);
        // This milestone never persists line items; force empty regardless of parser output.
        ExtractionResult headersOnly = ExtractionResult.ofHeaders(
                parsed.rawOcrText() != null ? parsed.rawOcrText() : ocrResult.rawText(),
                parsed.merchant(),
                parsed.date(),
                parsed.totalAmount(),
                parsed.currency(),
                parsed.categoryId()
        );
        return extractionValidator.validate(headersOnly, categories);
    }

    private List<CategoryOption> loadActiveCategories() {
        // The receipt header is a top-level group. Leaves are not offered here.
        return categoryRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .filter(category -> category.getParentId() == null)
                .map(this::toCategoryOption)
                .toList();
    }

    private CategoryOption toCategoryOption(Category category) {
        return new CategoryOption(category.getId(), category.getName(), category.getSlug());
    }
}

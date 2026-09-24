package com.financetracker.document.extraction;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Main Responsibility: Verify ExtractionPipeline with fake gateway + fake parser.
 *
 * Covers success, partial header, empty text (no usable header), missing collaborators,
 * and that a leaf category is not offered for the receipt header.
 */
class ExtractionPipelineTest {

    @TempDir
    Path tempDir;

    private DocumentTextGateway textGateway;
    private ReceiptParser receiptParser;
    private CategoryRepository categoryRepository;
    private ExtractionPipeline pipeline;
    private Path sampleFile;

    @BeforeEach
    void setUp() throws Exception {
        textGateway = mock(DocumentTextGateway.class);
        receiptParser = mock(ReceiptParser.class);
        categoryRepository = mock(CategoryRepository.class);

        Category food = new Category();
        food.setId(1L);
        food.setName("Food & Drink");
        food.setSlug("food");
        food.setActive(true);
        when(categoryRepository.findByIsActiveTrueOrderByNameAsc()).thenReturn(List.of(food));

        pipeline = new ExtractionPipeline(
                providerOf(textGateway),
                providerOf(receiptParser),
                new ExtractionValidator(),
                categoryRepository
        );

        sampleFile = tempDir.resolve("receipt.png");
        Files.write(sampleFile, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
    }

    @Test
    void extractReturnsValidatedSuccessHeaders() {
        OcrResult ocr = new OcrResult("Cafe Total 12.50", List.of());
        when(textGateway.extractText(eq(sampleFile), eq("image/png"))).thenReturn(ocr);
        when(receiptParser.parse(eq(ocr), anyList())).thenReturn(
                ExtractionResult.ofHeaders(
                        ocr.rawText(),
                        "Cafe",
                        LocalDate.of(2024, 6, 1),
                        new BigDecimal("12.50"),
                        "USD",
                        1L
                )
        );

        ExtractionResult result = pipeline.extract(sampleFile, "image/png");

        assertTrue(result.hasUsableHeader());
        assertEquals("Cafe", result.merchant());
        assertEquals(LocalDate.of(2024, 6, 1), result.date());
        assertEquals(0, new BigDecimal("12.50").compareTo(result.totalAmount()));
        assertEquals("EUR", result.currency());
        assertEquals(1L, result.categoryId());
        assertTrue(result.lineItems().isEmpty());
    }

    @Test
    void extractAllowsPartialHeaderForReview() {
        OcrResult ocr = new OcrResult("TOTAL 9.99", List.of());
        when(textGateway.extractText(any(), any())).thenReturn(ocr);
        // Merchant and category missing — still usable because amount is present.
        when(receiptParser.parse(any(), anyList())).thenReturn(
                ExtractionResult.ofHeaders(
                        ocr.rawText(),
                        null,
                        null,
                        new BigDecimal("9.99"),
                        "EUR",
                        null
                )
        );

        ExtractionResult result = pipeline.extract(sampleFile, "image/jpeg");

        assertTrue(result.hasUsableHeader());
        assertNull(result.merchant());
        assertNull(result.categoryId());
        assertEquals(0, new BigDecimal("9.99").compareTo(result.totalAmount()));
        assertEquals("EUR", result.currency());
    }

    @Test
    void extractEmptyTextSkipsParserAndReturnsNoUsableHeader() {
        when(textGateway.extractText(any(), any())).thenReturn(new OcrResult("   ", List.of()));

        ExtractionResult result = pipeline.extract(sampleFile, "image/png");

        assertFalse(result.hasUsableHeader());
        assertNull(result.merchant());
        assertNull(result.totalAmount());
        verify(receiptParser, never()).parse(any(), anyList());
    }

    @Test
    void extractOffersOnlyTopLevelCategoriesToTheParser() {
        Category meat = new Category();
        meat.setId(11L);
        meat.setName("Meat");
        meat.setSlug("meat");
        meat.setActive(true);
        meat.setParentId(1L);

        Category food = new Category();
        food.setId(1L);
        food.setName("Food & Drink");
        food.setSlug("food");
        food.setActive(true);
        when(categoryRepository.findByIsActiveTrueOrderByNameAsc()).thenReturn(List.of(food, meat));

        OcrResult ocr = new OcrResult("Meat 8.00", List.of());
        when(textGateway.extractText(eq(sampleFile), eq("image/png"))).thenReturn(ocr);
        when(receiptParser.parse(eq(ocr), anyList())).thenReturn(
                ExtractionResult.ofHeaders(
                        ocr.rawText(),
                        "Lidl",
                        null,
                        new BigDecimal("8.00"),
                        "EUR",
                        1L
                )
        );

        pipeline.extract(sampleFile, "image/png");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CategoryOption>> captor = ArgumentCaptor.forClass(List.class);
        verify(receiptParser).parse(eq(ocr), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("food", captor.getValue().get(0).slug());
    }

    @Test
    void extractFailsWhenGatewayOrParserMissing() {
        ExtractionPipeline noGateway = new ExtractionPipeline(
                providerOf(null),
                providerOf(receiptParser),
                new ExtractionValidator(),
                categoryRepository
        );
        ExtractionPipeline noParser = new ExtractionPipeline(
                providerOf(textGateway),
                providerOf(null),
                new ExtractionValidator(),
                categoryRepository
        );

        ExtractionException missingGateway = assertThrows(
                ExtractionException.class,
                () -> noGateway.extract(sampleFile, "image/png")
        );
        ExtractionException missingParser = assertThrows(
                ExtractionException.class,
                () -> noParser.extract(sampleFile, "image/png")
        );
        assertTrue(missingGateway.getMessage().contains("not configured"));
        assertTrue(missingParser.getMessage().contains("not configured"));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        return provider;
    }
}

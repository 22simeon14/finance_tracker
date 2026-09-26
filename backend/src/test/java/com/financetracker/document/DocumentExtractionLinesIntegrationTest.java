package com.financetracker.document;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.document.extraction.ExtractionPipeline;
import com.financetracker.document.extraction.ExtractionResult;
import com.financetracker.document.extraction.LineItemProposal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Main Responsibility: Prove extraction line items are persisted and returned
 * on GET /documents/{id} (review JSON), and cleared on continue-manual.
 *
 * Mocks ExtractionPipeline so the test does not need OCR/Groq. Uses the same
 * Testcontainers Postgres + migration mount pattern as ApiIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DocumentExtractionLinesIntegrationTest {

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    );

    @TempDir
    static Path uploadRoot;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("finance_tracker_lines_test")
            .withUsername("test")
            .withPassword("test")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/migrations/001_create_mvp_schema.sql"),
                    "/docker-entrypoint-initdb.d/001_create_mvp_schema.sql"
            )
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/migrations/002_seed_categories.sql"),
                    "/docker-entrypoint-initdb.d/002_seed_categories.sql"
            )
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/migrations/003_currency_eur_only.sql"),
                    "/docker-entrypoint-initdb.d/003_currency_eur_only.sql"
            )
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/migrations/004_category_parent.sql"),
                    "/docker-entrypoint-initdb.d/004_category_parent.sql"
            )
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/migrations/005_document_extraction_lines.sql"),
                    "/docker-entrypoint-initdb.d/005_document_extraction_lines.sql"
            )
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/migrations/006_extraction_line_qty_unit_price.sql"),
                    "/docker-entrypoint-initdb.d/006_extraction_line_qty_unit_price.sql"
            );

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("SPRING_DATASOURCE_URL", postgres::getJdbcUrl);
        registry.add("POSTGRES_USER", postgres::getUsername);
        registry.add("POSTGRES_PASSWORD", postgres::getPassword);
        registry.add("JWT_SECRET", () -> "integration-test-jwt-secret");
        registry.add("UPLOAD_DIR", () -> uploadRoot.toAbsolutePath().toString());
        registry.add("OCR_BASE_URL", () -> "");
        registry.add("GROQ_API_KEY", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @MockBean
    private ExtractionPipeline extractionPipeline;

    private String uniqueEmail;
    private long foodCategoryId;
    private long meatCategoryId;
    private long toiletriesCategoryId;

    @BeforeEach
    void setUp() {
        uniqueEmail = "lines-" + UUID.randomUUID() + "@example.com";
        foodCategoryId = categoryIdBySlug("food");
        meatCategoryId = categoryIdBySlug("meat");
        toiletriesCategoryId = categoryIdBySlug("toiletries");
    }

    @Test
    void upload_persistsLineItemsInReviewJson() throws Exception {
        when(extractionPipeline.extract(any(Path.class), anyString())).thenReturn(lidlResult());

        String token = registerAndLogin(uniqueEmail, "password123");
        long documentId = uploadTinyPng(token, "REVIEW_REQUIRED");

        mockMvc.perform(get("/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.extraction.proposedMerchant").value("Lidl"))
                .andExpect(jsonPath("$.extraction.lineItems.length()").value(2))
                .andExpect(jsonPath("$.extraction.lineItems[0].description").value("Minced meat"))
                .andExpect(jsonPath("$.extraction.lineItems[0].quantity").value(1.0))
                .andExpect(jsonPath("$.extraction.lineItems[0].unitPrice").value(8.0))
                .andExpect(jsonPath("$.extraction.lineItems[0].amount").value(8.0))
                .andExpect(jsonPath("$.extraction.lineItems[0].categoryId").value((int) meatCategoryId))
                .andExpect(jsonPath("$.extraction.lineItems[1].description").value("Soap"))
                .andExpect(jsonPath("$.extraction.lineItems[1].quantity").value(nullValue()))
                .andExpect(jsonPath("$.extraction.lineItems[1].unitPrice").value(nullValue()))
                .andExpect(jsonPath("$.extraction.lineItems[1].amount").value(4.0))
                .andExpect(jsonPath("$.extraction.lineItems[1].categoryId").value((int) toiletriesCategoryId));
    }

    @Test
    void continueManual_clearsPersistedLineItems() throws Exception {
        when(extractionPipeline.extract(any(Path.class), anyString())).thenReturn(lidlResult());

        String token = registerAndLogin(uniqueEmail, "password123");
        long documentId = uploadTinyPng(token, "REVIEW_REQUIRED");

        mockMvc.perform(get("/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraction.lineItems.length()").value(2));

        // continue-manual is only allowed from PROCESSING_FAILED; flip status in DB
        // so we can exercise the clear path without a second public re-process API.
        Document document = documentRepository.findById(documentId).orElseThrow();
        document.setStatus("PROCESSING_FAILED");
        documentRepository.save(document);

        mockMvc.perform(post("/documents/" + documentId + "/continue-manual")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.extraction.proposedMerchant").value(nullValue()))
                .andExpect(jsonPath("$.extraction.lineItems.length()").value(0));

        mockMvc.perform(get("/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraction.lineItems.length()").value(0));
    }

    private ExtractionResult lidlResult() {
        return new ExtractionResult(
                "Lidl receipt text",
                "Lidl",
                LocalDate.of(2024, 6, 15),
                new BigDecimal("47.00"),
                "EUR",
                foodCategoryId,
                List.of(
                        new LineItemProposal(
                                "Minced meat",
                                new BigDecimal("1"),
                                new BigDecimal("8.00"),
                                new BigDecimal("8.00"),
                                meatCategoryId
                        ),
                        LineItemProposal.of("Soap", new BigDecimal("4.00"), toiletriesCategoryId)
                )
        );
    }

    private long categoryIdBySlug(String slug) {
        return categoryRepository.findAll().stream()
                .filter(category -> slug.equals(category.getSlug()))
                .map(Category::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing category slug: " + slug));
    }

    private String registerAndLogin(String email, String password) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, password)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return readJsonField(loginResult, "token");
    }

    private long uploadTinyPng(String token, String expectedStatus) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tiny.png",
                "image/png",
                TINY_PNG
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/documents")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andReturn();

        return Long.parseLong(readJsonField(uploadResult, "id"));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String authJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private static String readJsonField(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString();
        String quotedKey = "\"" + field + "\"";
        int keyIndex = body.indexOf(quotedKey);
        if (keyIndex < 0) {
            throw new IllegalStateException("Field " + field + " not found in: " + body);
        }
        int colon = body.indexOf(':', keyIndex + quotedKey.length());
        if (colon < 0) {
            throw new IllegalStateException("Missing ':' after field " + field + " in: " + body);
        }
        int valueStart = colon + 1;
        while (valueStart < body.length() && Character.isWhitespace(body.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= body.length()) {
            throw new IllegalStateException("Empty value for " + field + " in: " + body);
        }
        if (body.charAt(valueStart) == '"') {
            int end = body.indexOf('"', valueStart + 1);
            if (end < 0) {
                throw new IllegalStateException("Unclosed string value for " + field + " in: " + body);
            }
            return body.substring(valueStart + 1, end);
        }
        int end = valueStart;
        if (end < body.length() && body.charAt(end) == '-') {
            end++;
        }
        int digitsStart = end;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        if (end == digitsStart) {
            throw new IllegalStateException("Expected number value for " + field + " in: " + body);
        }
        return body.substring(valueStart, end);
    }
}

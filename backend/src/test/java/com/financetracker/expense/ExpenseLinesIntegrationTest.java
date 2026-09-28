package com.financetracker.expense;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.document.DocumentExtraction;
import com.financetracker.document.DocumentExtractionLine;
import com.financetracker.document.DocumentExtractionLineRepository;
import com.financetracker.document.DocumentExtractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Main Responsibility: Prove approve writes expense_lines from the request body,
 * rejects a bad line without creating an expense, unapprove drops those lines
 * while extraction proposals stay as they were, and GET/PUT expense detail
 * returns and replaces confirmed lines.
 *
 * OCR/Groq stay off. continue-manual reaches REVIEW_REQUIRED without network.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ExpenseLinesIntegrationTest {

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    static Path uploadRoot;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("finance_tracker_expense_lines_test")
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
            )
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/migrations/007_expense_lines.sql"),
                    "/docker-entrypoint-initdb.d/007_expense_lines.sql"
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
    private CategoryRepository categoryRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseLineRepository expenseLineRepository;

    @Autowired
    private DocumentExtractionRepository documentExtractionRepository;

    @Autowired
    private DocumentExtractionLineRepository documentExtractionLineRepository;

    private String uniqueEmail;
    private long foodCategoryId;
    private long meatCategoryId;

    @BeforeEach
    void setUp() {
        uniqueEmail = "lines-" + UUID.randomUUID() + "@example.com";
        foodCategoryId = categoryIdBySlug("food");
        meatCategoryId = categoryIdBySlug("meat");
    }

    @Test
    void approve_persistsRequestLines_secondApproveConflicts_unapproveKeepsExtraction() throws Exception {
        String token = registerAndLogin(uniqueEmail, "password123");
        long documentId = uploadAndContinueManual(token);
        seedExtractionProposal(documentId, "OCR proposal");

        String body = approveJson(
                "Lidl",
                "2026-03-01",
                "47.00",
                foodCategoryId,
                """
                        [
                          {"description":"Minced meat","quantity":1,"unitPrice":8.00,"amount":8.00,"categoryId":%d},
                          {"description":"Soap","amount":4.00,"categoryId":null}
                        ]
                        """.formatted(meatCategoryId)
        );

        MvcResult approveResult = mockMvc.perform(post("/documents/" + documentId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        long expenseId = objectMapper.readTree(approveResult.getResponse().getContentAsString()).get("id").asLong();

        List<ExpenseLine> lines = expenseLineRepository.findByExpenseIdOrderByPositionAsc(expenseId);
        assertEquals(2, lines.size());
        assertEquals("Minced meat", lines.get(0).getDescription());
        assertEquals(0, new BigDecimal("1").compareTo(lines.get(0).getQuantity()));
        assertEquals(0, new BigDecimal("8.00").compareTo(lines.get(0).getUnitPrice()));
        assertEquals(0, new BigDecimal("8.00").compareTo(lines.get(0).getAmount()));
        assertEquals(meatCategoryId, lines.get(0).getCategoryId());
        assertEquals(0, lines.get(0).getPosition());
        assertEquals("Soap", lines.get(1).getDescription());
        assertNull(lines.get(1).getQuantity());
        assertNull(lines.get(1).getUnitPrice());
        assertEquals(0, new BigDecimal("4.00").compareTo(lines.get(1).getAmount()));
        assertNull(lines.get(1).getCategoryId());
        assertEquals(1, lines.get(1).getPosition());

        mockMvc.perform(get("/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andExpect(jsonPath("$.extraction.lineItems[0].description").value("OCR proposal"));

        mockMvc.perform(post("/documents/" + documentId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/expenses/" + expenseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        assertFalse(expenseRepository.existsByDocumentId(documentId));
        assertTrue(expenseLineRepository.findByExpenseIdOrderByPositionAsc(expenseId).isEmpty());

        mockMvc.perform(get("/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.extraction.lineItems.length()").value(1))
                .andExpect(jsonPath("$.extraction.lineItems[0].description").value("OCR proposal"));
    }

    @Test
    void approve_emptyLineList_createsExpenseWithNoLines() throws Exception {
        String token = registerAndLogin(uniqueEmail, "password123");
        long documentId = uploadAndContinueManual(token);

        MvcResult approveResult = mockMvc.perform(post("/documents/" + documentId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson("Shop", "2026-03-02", "12.50", foodCategoryId, "[]")))
                .andExpect(status().isCreated())
                .andReturn();

        long expenseId = objectMapper.readTree(approveResult.getResponse().getContentAsString()).get("id").asLong();
        assertTrue(expenseLineRepository.findByExpenseIdOrderByPositionAsc(expenseId).isEmpty());
    }

    @Test
    void approve_invalidLine_returns400AndCreatesNoExpense() throws Exception {
        String token = registerAndLogin(uniqueEmail, "password123");
        long documentId = uploadAndContinueManual(token);

        mockMvc.perform(post("/documents/" + documentId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson(
                                "Shop",
                                "2026-03-03",
                                "10.00",
                                foodCategoryId,
                                "[{\"description\":\"\",\"amount\":1.00}]"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));

        mockMvc.perform(post("/documents/" + documentId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson(
                                "Shop",
                                "2026-03-03",
                                "10.00",
                                foodCategoryId,
                                "[{\"description\":\"Bread\",\"amount\":0}]"
                        )))
                .andExpect(status().isBadRequest());

        assertFalse(expenseRepository.existsByDocumentId(documentId));
        mockMvc.perform(get("/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"));
    }

    @Test
    void approve_inactiveLineCategory_returns400AndCreatesNoExpense() throws Exception {
        String token = registerAndLogin(uniqueEmail, "password123");
        long documentId = uploadAndContinueManual(token);

        Category inactive = new Category();
        inactive.setName("Inactive " + uniqueEmail);
        inactive.setSlug("inactive-" + UUID.randomUUID());
        inactive.setActive(false);
        inactive = categoryRepository.saveAndFlush(inactive);

        mockMvc.perform(post("/documents/" + documentId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson(
                                "Shop",
                                "2026-03-04",
                                "10.00",
                                foodCategoryId,
                                "[{\"description\":\"Old stock\",\"amount\":2.00,\"categoryId\":%d}]"
                                        .formatted(inactive.getId())
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Line item category must exist and be active"));

        assertFalse(expenseRepository.existsByDocumentId(documentId));
        mockMvc.perform(get("/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"));
    }

    @Test
    void getAndPut_expense_returnsAndReplacesLines_listOmitsLineBodies() throws Exception {
        String token = registerAndLogin(uniqueEmail, "password123");
        long documentId = uploadAndContinueManual(token);

        MvcResult approveResult = mockMvc.perform(post("/documents/" + documentId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson(
                                "Lidl",
                                "2026-03-05",
                                "47.00",
                                foodCategoryId,
                                """
                                        [
                                          {"description":"Minced meat","quantity":1,"unitPrice":8.00,"amount":8.00,"categoryId":%d},
                                          {"description":"Soap","amount":4.00}
                                        ]
                                        """.formatted(meatCategoryId)
                        )))
                .andExpect(status().isCreated())
                .andReturn();

        long expenseId = objectMapper.readTree(approveResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/expenses/" + expenseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems.length()").value(2))
                .andExpect(jsonPath("$.lineItems[0].description").value("Minced meat"))
                .andExpect(jsonPath("$.lineItems[0].categoryId").value(meatCategoryId))
                .andExpect(jsonPath("$.lineItems[1].description").value("Soap"))
                .andExpect(jsonPath("$.lineItems[1].categoryId").value(nullValue()));

        // List stays one purchase row: lineItems is an empty array, not the breakdown.
        mockMvc.perform(get("/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(expenseId))
                .andExpect(jsonPath("$[0].lineItems.length()").value(0));

        mockMvc.perform(put("/expenses/" + expenseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(
                                "Lidl",
                                "2026-03-05",
                                "47.00",
                                foodCategoryId,
                                """
                                        [
                                          {"description":"Minced meat","quantity":1,"unitPrice":8.00,"amount":8.00,"categoryId":%d},
                                          {"description":"Soap","amount":4.00,"categoryId":%d}
                                        ]
                                        """.formatted(meatCategoryId, categoryIdBySlug("toiletries"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems.length()").value(2))
                .andExpect(jsonPath("$.lineItems[1].description").value("Soap"))
                .andExpect(jsonPath("$.lineItems[1].categoryId").value(categoryIdBySlug("toiletries")));

        List<ExpenseLine> lines = expenseLineRepository.findByExpenseIdOrderByPositionAsc(expenseId);
        assertEquals(2, lines.size());
        assertEquals(categoryIdBySlug("toiletries"), lines.get(1).getCategoryId());

        // Empty list clears confirmed lines without deleting the expense.
        mockMvc.perform(put("/expenses/" + expenseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson("Lidl", "2026-03-05", "47.00", foodCategoryId, "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems.length()").value(0));

        assertTrue(expenseLineRepository.findByExpenseIdOrderByPositionAsc(expenseId).isEmpty());
        assertTrue(expenseRepository.existsById(expenseId));
    }

    @Test
    void put_invalidLine_returns400AndKeepsPreviousLines() throws Exception {
        String token = registerAndLogin(uniqueEmail, "password123");
        long documentId = uploadAndContinueManual(token);

        MvcResult approveResult = mockMvc.perform(post("/documents/" + documentId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson(
                                "Shop",
                                "2026-03-06",
                                "10.00",
                                foodCategoryId,
                                "[{\"description\":\"Bread\",\"amount\":3.00,\"categoryId\":%d}]"
                                        .formatted(meatCategoryId)
                        )))
                .andExpect(status().isCreated())
                .andReturn();

        long expenseId = objectMapper.readTree(approveResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/expenses/" + expenseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeJson(
                                "Shop",
                                "2026-03-06",
                                "10.00",
                                foodCategoryId,
                                "[{\"description\":\"\",\"amount\":1.00}]"
                        )))
                .andExpect(status().isBadRequest());

        List<ExpenseLine> lines = expenseLineRepository.findByExpenseIdOrderByPositionAsc(expenseId);
        assertEquals(1, lines.size());
        assertEquals("Bread", lines.get(0).getDescription());
        assertEquals(meatCategoryId, lines.get(0).getCategoryId());
    }

    private void seedExtractionProposal(long documentId, String description) {
        DocumentExtraction extraction = documentExtractionRepository.findByDocumentId(documentId)
                .orElseThrow();
        DocumentExtractionLine proposal = new DocumentExtractionLine();
        proposal.setExtractionId(extraction.getId());
        proposal.setDescription(description);
        proposal.setAmount(new BigDecimal("3.00"));
        proposal.setPosition(0);
        documentExtractionLineRepository.saveAndFlush(proposal);
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
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    private long uploadAndContinueManual(String token) throws Exception {
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
                .andReturn();

        long documentId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/documents/" + documentId + "/continue-manual")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"));

        return documentId;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String approveJson(
            String merchant,
            String date,
            String amount,
            long categoryId,
            String lineItemsJson
    ) {
        return """
                {
                  "merchant": "%s",
                  "expenseDate": "%s",
                  "totalAmount": %s,
                  "currency": "EUR",
                  "categoryId": %d,
                  "lineItems": %s
                }
                """.formatted(merchant, date, amount, categoryId, lineItemsJson);
    }

    /** Same shape as approve — used for PUT /expenses/{id}. */
    private static String writeJson(
            String merchant,
            String date,
            String amount,
            long categoryId,
            String lineItemsJson
    ) {
        return approveJson(merchant, date, amount, categoryId, lineItemsJson);
    }
}

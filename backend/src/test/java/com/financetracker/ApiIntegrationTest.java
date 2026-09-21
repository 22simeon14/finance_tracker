package com.financetracker;

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

import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Main Responsibility: Happy-path API contract tests against real Postgres.
 *
 * Uses Testcontainers (no H2). OCR/Groq stay off so upload ends in PROCESSING_FAILED,
 * then continue-manual + approve exercises ownership and atomic SAVED without network.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiIntegrationTest {

    /**
     * Minimal valid 1x1 PNG (avoids a binary fixture file in git).
     * Base64 keeps the bytes readable in source.
     */
    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    );

    /** Seeded by 002_seed_categories.sql — Food & Drink is always id 1 on a fresh DB. */
    private static final long FOOD_CATEGORY_ID = 1L;

    @TempDir
    static Path uploadRoot;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("finance_tracker_test")
            .withUsername("test")
            .withPassword("test")
            // Same init order as Compose: mount SQL into docker-entrypoint-initdb.d.
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
            );

    /**
     * Wire JDBC + required app env placeholders from application.yml.
     * Empty OCR/Groq → HttpOcrClient / GroqReceiptParser beans stay off.
     */
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

    private String uniqueEmail;

    @BeforeEach
    void assignUniqueEmail() {
        uniqueEmail = "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void registerLoginAndMe_returnTokenAndCurrentUser() throws Exception {
        String password = "password123";

        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(uniqueEmail, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andReturn();

        String registerToken = readJsonField(registerResult, "token");

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(uniqueEmail))
                .andExpect(jsonPath("$.id", notNullValue()));

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(uniqueEmail, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andReturn();

        String loginToken = readJsonField(loginResult, "token");

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(uniqueEmail));
    }

    @Test
    void uploadOwnership_foreignGetReturns404() throws Exception {
        String userAToken = registerAndLogin("a-" + uniqueEmail, "password123");
        String userBToken = registerAndLogin("b-" + uniqueEmail, "password123");

        long documentId = uploadTinyPng(userAToken);

        mockMvc.perform(get("/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId))
                .andExpect(jsonPath("$.status").value("PROCESSING_FAILED"));

        mockMvc.perform(get("/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userBToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void continueManualAndApprove_createsExpenseAndSecondApproveConflicts() throws Exception {
        String token = registerAndLogin(uniqueEmail, "password123");
        long documentId = uploadTinyPng(token);

        mockMvc.perform(post("/documents/" + documentId + "/continue-manual")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"));

        MvcResult approveResult = mockMvc.perform(post("/documents/" + documentId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson("Test Merchant", "2026-01-15", "12.50", FOOD_CATEGORY_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.documentId").value(documentId))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andReturn();

        long expenseId = Long.parseLong(readJsonField(approveResult, "id"));

        mockMvc.perform(get("/documents/" + documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SAVED"));

        mockMvc.perform(get("/expenses/" + expenseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(expenseId))
                .andExpect(jsonPath("$.documentId").value(documentId));

        mockMvc.perform(post("/documents/" + documentId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson("Test Merchant", "2026-01-15", "12.50", FOOD_CATEGORY_ID)))
                .andExpect(status().isConflict());
    }

    @Test
    void foreignExpense_getReturns404() throws Exception {
        String userAToken = registerAndLogin("a-" + uniqueEmail, "password123");
        String userBToken = registerAndLogin("b-" + uniqueEmail, "password123");

        long documentId = uploadTinyPng(userAToken);

        mockMvc.perform(post("/documents/" + documentId + "/continue-manual")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAToken)))
                .andExpect(status().isOk());

        MvcResult approveResult = mockMvc.perform(post("/documents/" + documentId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson("Owned Merchant", "2026-02-01", "9.99", FOOD_CATEGORY_ID)))
                .andExpect(status().isCreated())
                .andReturn();

        long expenseId = Long.parseLong(readJsonField(approveResult, "id"));

        mockMvc.perform(get("/expenses/" + expenseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userBToken)))
                .andExpect(status().isNotFound());
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

    /** Upload with empty OCR/Groq → PROCESSING_FAILED (asserted by callers that need it). */
    private long uploadTinyPng(String token) throws Exception {
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
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status").value("PROCESSING_FAILED"))
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

    private static String approveJson(String merchant, String date, String amount, long categoryId) {
        return """
                {
                  "merchant": "%s",
                  "expenseDate": "%s",
                  "totalAmount": %s,
                  "currency": "EUR",
                  "categoryId": %d
                }
                """.formatted(merchant, date, amount, categoryId);
    }

    /**
     * Tiny JSON field reader for test payloads (token / id). Avoids adding a JSON test library.
     * Expects a top-level string or number field like "token":"..." or "id":12.
     * Fail loudly on malformed JSON so tests do not silently parse the wrong slice.
     */
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
        // Optional leading minus, then digits only (ids / categoryId — not free-form numbers).
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

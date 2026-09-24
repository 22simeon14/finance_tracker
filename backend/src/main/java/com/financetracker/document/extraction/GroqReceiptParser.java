package com.financetracker.document.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main Responsibility: Call Groq Cloud chat completions and map JSON to ExtractionResult.
 *
 * Uses the OpenAI-compatible HTTP shape at GROQ_API_BASE_URL (not api.openai.com / xAI).
 * Sends OCR text only — never image bytes. Currency is always coerced to EUR. Category
 * maps only when categorySlug matches an active option (group or leaf); otherwise
 * categoryId stays null and the line is kept. Active when app.groq.api-key is non-blank.
 */
@Service
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${app.groq.api-key:}')")
public class GroqReceiptParser implements ReceiptParser {

    private static final String EUR = "EUR";
    private static final double TEMPERATURE = 0.0;

    /**
     * Field contract told to the model (llama-3.3 supports json_object, not constrained
     * json_schema decoding on Groq). Keep in sync with mapContentToResult.
     */
    static final String RECEIPT_HEADER_SCHEMA = """
            {
              "merchant": string or null,
              "date": "YYYY-MM-DD" string or null,
              "totalAmount": number or null,
              "currency": string or null,
              "categorySlug": string or null,
              "lineItems": [
                {
                  "description": string,
                  "amount": number,
                  "categorySlug": string or null
                }
              ]
            }
            """;

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;

    // @Autowired is required: two constructors exist (this + test-only RestClient one).
    @Autowired
    public GroqReceiptParser(
            @Value("${app.groq.api-key}") String apiKey,
            @Value("${app.groq.api-base-url}") String apiBaseUrl,
            @Value("${app.groq.model}") String model,
            @Value("${app.groq.timeout-ms:20000}") long timeoutMs,
            ObjectMapper objectMapper
    ) {
        this(buildRestClient(apiBaseUrl, apiKey, timeoutMs), model, objectMapper);
    }

    /** Package-visible for unit tests with MockRestServiceServer. */
    GroqReceiptParser(RestClient restClient, String model, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExtractionResult parse(OcrResult ocrResult, List<CategoryOption> categories) {
        if (ocrResult == null || ocrResult.isBlank()) {
            throw new ExtractionException("OCR text is empty; cannot call receipt parser");
        }

        Map<String, Object> requestBody = buildRequest(ocrResult.rawText(), categories);
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            String content = extractMessageContent(response);
            return mapContentToResult(content, ocrResult.rawText(), categories);
        } catch (RestClientException exception) {
            throw new ExtractionException("Groq receipt parser request failed", exception);
        }
    }

    private Map<String, Object> buildRequest(String rawText, List<CategoryOption> categories) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", TEMPERATURE);
        // json_object works with the pinned llama model; schema lives in the system prompt.
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", buildSystemPrompt(categories)),
                Map.of("role", "user", "content", "Receipt OCR text:\n" + rawText)
        ));
        return body;
    }

    private static String buildSystemPrompt(List<CategoryOption> categories) {
        String allowedSlugs = allowedSlugList(categories);
        return """
                You extract receipt/invoice header fields and purchased line items from OCR text.
                Respond with a single JSON object only (no markdown), matching this schema:
                %s
                Rules:
                - Use null when a field is unknown or not present in the text.
                - date must be ISO-8601 YYYY-MM-DD when known.
                - totalAmount is the final amount charged (not tax alone); use a JSON number.
                - currency may appear as printed (EUR, USD, BGN, etc.); the server forces EUR later.
                - categorySlug (receipt) should be a broad group when possible; must be one of [%s] or null.
                - lineItems: one object per purchased product line when visible; use [] if none.
                - each lineItems[].categorySlug must be one of [%s] or null. Never invent other slugs.
                - lineItems[].amount is that line's price as a JSON number; description is the product text.
                """.formatted(RECEIPT_HEADER_SCHEMA.strip(), allowedSlugs, allowedSlugs);
    }

    private static String allowedSlugList(List<CategoryOption> categories) {
        if (categories == null || categories.isEmpty()) {
            return "";
        }
        return categories.stream()
                .filter(option -> option != null && option.slug() != null && !option.slug().isBlank())
                .map(option -> option.slug().trim())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private static String extractMessageContent(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new ExtractionException("Groq returned no chat choices");
        }
        ChatChoice first = response.choices().getFirst();
        if (first == null || first.message() == null) {
            throw new ExtractionException("Groq returned an empty message");
        }
        String content = first.message().content();
        if (content == null || content.isBlank()) {
            throw new ExtractionException("Groq returned empty message content");
        }
        return content;
    }

    private ExtractionResult mapContentToResult(
            String content,
            String rawOcrText,
            List<CategoryOption> categories
    ) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (Exception exception) {
            throw new ExtractionException("Groq returned unparseable JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new ExtractionException("Groq JSON root is not an object");
        }

        String merchant = textOrNull(root.get("merchant"));
        LocalDate date = parseDate(root.get("date"));
        BigDecimal totalAmount = parseAmount(root.get("totalAmount"));
        Long categoryId = mapCategoryId(textOrNull(root.get("categorySlug")), categories);
        List<LineItemProposal> lineItems = mapLineItems(root.get("lineItems"), categories);

        // Product rule: store EUR even when the model prints BGN/USD/GBP.
        return new ExtractionResult(
                rawOcrText,
                merchant,
                date,
                totalAmount,
                EUR,
                categoryId,
                lineItems
        );
    }

    /**
     * Map each JSON line. Unknown categorySlug → null categoryId; the row is still kept
     * so the validator can drop only description/amount problems.
     */
    private static List<LineItemProposal> mapLineItems(
            JsonNode arrayNode,
            List<CategoryOption> categories
    ) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<LineItemProposal> items = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            if (node == null || !node.isObject()) {
                continue;
            }
            String description = textOrNull(node.get("description"));
            BigDecimal amount = parseAmount(node.get("amount"));
            Long categoryId = mapCategoryId(textOrNull(node.get("categorySlug")), categories);
            items.add(new LineItemProposal(description, amount, categoryId));
        }
        return List.copyOf(items);
    }

    private static Long mapCategoryId(String categorySlug, List<CategoryOption> categories) {
        if (categorySlug == null || categories == null) {
            return null;
        }
        String needle = categorySlug.trim().toLowerCase(Locale.ROOT);
        Map<String, Long> bySlug = new HashMap<>();
        for (CategoryOption option : categories) {
            if (option == null || option.slug() == null || option.id() == null) {
                continue;
            }
            bySlug.put(option.slug().trim().toLowerCase(Locale.ROOT), option.id());
        }
        return bySlug.get(needle);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static LocalDate parseDate(JsonNode node) {
        String text = textOrNull(node);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {
            // Bad date → leave null; pipeline may still have other usable headers.
            return null;
        }
    }

    private static BigDecimal parseAmount(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return new BigDecimal(text.trim().replace(',', '.'));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static RestClient buildRestClient(String apiBaseUrl, String apiKey, long timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // Sync document processing waits on the LLM; keep within the plan's ~20s budget.
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder()
                .baseUrl(apiBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .requestFactory(requestFactory)
                .build();
    }

    /** Minimal OpenAI-compatible chat completion response (fields we read). */
    record ChatCompletionResponse(List<ChatChoice> choices) {
    }

    record ChatChoice(ChatMessage message) {
    }

    record ChatMessage(String content) {
    }
}

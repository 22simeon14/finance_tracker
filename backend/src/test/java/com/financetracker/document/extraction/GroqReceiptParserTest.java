package com.financetracker.document.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Main Responsibility: Verify GroqReceiptParser prompt/schema, EUR coercion, slug map, lines.
 */
class GroqReceiptParserTest {

    private static final List<CategoryOption> CATEGORIES = List.of(
            new CategoryOption(1L, "Food & Drink", "food"),
            new CategoryOption(2L, "Transport", "transport"),
            new CategoryOption(11L, "Meat", "meat"),
            new CategoryOption(21L, "Toiletries", "toiletries")
    );

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private GroqReceiptParser parser;
    private final ExtractionValidator validator = new ExtractionValidator();

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder()
                .baseUrl("http://groq.local")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-key");
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        parser = new GroqReceiptParser(
                restClientBuilder.build(),
                "llama-3.3-70b-versatile",
                new ObjectMapper()
        );
    }

    @Test
    void parseMapsHeadersCoercesEurAndResolvesCategorySlug() {
        server.expect(requestTo("http://groq.local/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                // Prompt carries the field schema and allowed slugs; response_format is json_object.
                .andExpect(content().string(Matchers.containsString("\"type\":\"json_object\"")))
                .andExpect(content().string(Matchers.containsString("merchant")))
                .andExpect(content().string(Matchers.containsString("categorySlug")))
                .andExpect(content().string(Matchers.containsString("lineItems")))
                .andExpect(content().string(Matchers.containsString("food")))
                .andExpect(content().string(Matchers.containsString("Cafe Central")))
                .andRespond(withSuccess(
                        """
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"merchant\\":\\"Cafe Central\\",\\"date\\":\\"2024-06-15\\",\\"totalAmount\\":12.5,\\"currency\\":\\"USD\\",\\"categorySlug\\":\\"food\\",\\"lineItems\\":[]}"
                              }
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        OcrResult ocr = new OcrResult("Cafe Central\nTotal 12.50 USD", List.of());
        ExtractionResult result = parser.parse(ocr, CATEGORIES);

        assertEquals("Cafe Central\nTotal 12.50 USD", result.rawOcrText());
        assertEquals("Cafe Central", result.merchant());
        assertEquals(LocalDate.of(2024, 6, 15), result.date());
        assertEquals(0, new BigDecimal("12.5").compareTo(result.totalAmount()));
        assertEquals("EUR", result.currency());
        assertEquals(1L, result.categoryId());
        assertTrue(result.lineItems().isEmpty());
        server.verify();
    }

    @Test
    void parseAndValidateKeepsTwoLineItemsWithCategoriesAndDropsInvalid() {
        // Sample Groq JSON: two good lines + one with amount 0 that the validator drops.
        server.expect(requestTo("http://groq.local/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.containsString("meat")))
                .andExpect(content().string(Matchers.containsString("toiletries")))
                .andRespond(withSuccess(
                        """
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"merchant\\":\\"Lidl\\",\\"date\\":\\"2024-06-15\\",\\"totalAmount\\":47,\\"currency\\":\\"EUR\\",\\"categorySlug\\":\\"food\\",\\"lineItems\\":[{\\"description\\":\\"Minced meat\\",\\"amount\\":8,\\"categorySlug\\":\\"meat\\"},{\\"description\\":\\"Soap\\",\\"amount\\":4,\\"categorySlug\\":\\"toiletries\\"},{\\"description\\":\\"Bad row\\",\\"amount\\":0,\\"categorySlug\\":\\"food\\"}]}"
                              }
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        ExtractionResult parsed = parser.parse(
                new OcrResult("Lidl meat soap", List.of()),
                CATEGORIES
        );
        ExtractionResult result = validator.validate(parsed, CATEGORIES);

        assertEquals(2, result.lineItems().size());
        assertEquals("Minced meat", result.lineItems().get(0).description());
        assertEquals(0, new BigDecimal("8").compareTo(result.lineItems().get(0).amount()));
        assertEquals(11L, result.lineItems().get(0).categoryId());
        assertEquals("Soap", result.lineItems().get(1).description());
        assertEquals(0, new BigDecimal("4").compareTo(result.lineItems().get(1).amount()));
        assertEquals(21L, result.lineItems().get(1).categoryId());
        server.verify();
    }

    @Test
    void parseKeepsLineWhenCategorySlugUnknown() {
        server.expect(requestTo("http://groq.local/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"merchant\\":\\"Shop\\",\\"date\\":null,\\"totalAmount\\":5,\\"currency\\":\\"EUR\\",\\"categorySlug\\":null,\\"lineItems\\":[{\\"description\\":\\"Mystery\\",\\"amount\\":5,\\"categorySlug\\":\\"not-a-real-slug\\"}]}"
                              }
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        ExtractionResult result = parser.parse(
                new OcrResult("Shop Mystery 5", List.of()),
                CATEGORIES
        );

        assertEquals(1, result.lineItems().size());
        assertEquals("Mystery", result.lineItems().get(0).description());
        assertNull(result.lineItems().get(0).categoryId());
        server.verify();
    }

    @Test
    void parseLeavesCategoryNullWhenSlugUnknown() {
        server.expect(requestTo("http://groq.local/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"merchant\\":\\"Shop\\",\\"date\\":null,\\"totalAmount\\":9.99,\\"currency\\":\\"BGN\\",\\"categorySlug\\":\\"not-a-real-slug\\",\\"lineItems\\":[]}"
                              }
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        ExtractionResult result = parser.parse(
                new OcrResult("Shop 9.99", List.of()),
                CATEGORIES
        );

        assertEquals("EUR", result.currency());
        assertNull(result.categoryId());
        assertEquals("Shop", result.merchant());
        server.verify();
    }

    @Test
    void parseFailsWhenGroqReturnsError() {
        server.expect(requestTo("http://groq.local/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        ExtractionException exception = assertThrows(
                ExtractionException.class,
                () -> parser.parse(new OcrResult("some text", List.of()), CATEGORIES)
        );
        assertTrue(exception.getMessage().contains("Groq receipt parser request failed"));
        server.verify();
    }

    @Test
    void parseFailsWhenMessageContentIsNotJson() {
        server.expect(requestTo("http://groq.local/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "choices": [
                            { "message": { "content": "not-json" } }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        ExtractionException exception = assertThrows(
                ExtractionException.class,
                () -> parser.parse(new OcrResult("some text", List.of()), CATEGORIES)
        );
        assertTrue(exception.getMessage().contains("unparseable JSON"));
        server.verify();
    }

    @Test
    void parseRejectsBlankOcrText() {
        ExtractionException exception = assertThrows(
                ExtractionException.class,
                () -> parser.parse(new OcrResult("   ", List.of()), CATEGORIES)
        );
        assertTrue(exception.getMessage().contains("empty"));
    }
}

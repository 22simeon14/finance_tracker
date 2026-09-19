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
 * Main Responsibility: Verify GroqReceiptParser prompt/schema, EUR coercion, and slug map.
 */
class GroqReceiptParserTest {

    private static final List<CategoryOption> CATEGORIES = List.of(
            new CategoryOption(1L, "Food & Drink", "food"),
            new CategoryOption(2L, "Transport", "transport")
    );

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private GroqReceiptParser parser;

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
                .andExpect(content().string(Matchers.containsString("food")))
                .andExpect(content().string(Matchers.containsString("Cafe Central")))
                .andRespond(withSuccess(
                        """
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"merchant\\":\\"Cafe Central\\",\\"date\\":\\"2024-06-15\\",\\"totalAmount\\":12.5,\\"currency\\":\\"USD\\",\\"categorySlug\\":\\"food\\"}"
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
    void parseLeavesCategoryNullWhenSlugUnknown() {
        server.expect(requestTo("http://groq.local/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"merchant\\":\\"Shop\\",\\"date\\":null,\\"totalAmount\\":9.99,\\"currency\\":\\"BGN\\",\\"categorySlug\\":\\"not-a-real-slug\\"}"
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

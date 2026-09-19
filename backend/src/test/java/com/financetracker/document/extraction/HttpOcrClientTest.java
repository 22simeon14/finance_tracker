package com.financetracker.document.extraction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Main Responsibility: Verify HttpOcrClient maps sidecar JSON and fails hard on errors.
 */
class HttpOcrClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private HttpOcrClient client;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder().baseUrl("http://ocr.local");
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new HttpOcrClient(restClientBuilder.build());
    }

    @Test
    void recognizeMapsTextAndBoundingBoxes() {
        server.expect(requestTo("http://ocr.local/ocr"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "text": "Cafe Central\\nTotal 12.50",
                          "lines": [
                            {"text": "Cafe Central", "bbox": {"x0": 1.0, "y0": 2.0, "x1": 10.0, "y1": 20.0}},
                            {"text": "Total 12.50", "bbox": null}
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        OcrResult result = client.recognize(new byte[]{1, 2, 3});

        assertEquals("Cafe Central\nTotal 12.50", result.rawText());
        assertEquals(2, result.lines().size());
        assertEquals("Cafe Central", result.lines().get(0).text());
        assertNotNull(result.lines().get(0).bbox());
        assertEquals(1.0, result.lines().get(0).bbox().x0());
        assertEquals("Total 12.50", result.lines().get(1).text());
        server.verify();
    }

    @Test
    void recognizeFailsWhenSidecarReturnsError() {
        server.expect(requestTo("http://ocr.local/ocr"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        ExtractionException exception = assertThrows(
                ExtractionException.class,
                () -> client.recognize(new byte[]{1, 2, 3})
        );
        assertTrue(exception.getMessage().contains("OCR sidecar request failed"));
        server.verify();
    }

    @Test
    void recognizeRejectsEmptyBytes() {
        ExtractionException exception = assertThrows(
                ExtractionException.class,
                () -> client.recognize(new byte[0])
        );
        assertTrue(exception.getMessage().contains("empty"));
    }
}

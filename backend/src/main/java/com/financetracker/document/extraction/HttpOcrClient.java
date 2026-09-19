package com.financetracker.document.extraction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

/**
 * Main Responsibility: Call the RapidOCR sidecar over HTTP and map JSON to OcrResult.
 *
 * Active when app.ocr.base-url is non-blank (Compose sets OCR_BASE_URL). Failures become
 * ExtractionException so DocumentService can mark PROCESSING_FAILED.
 */
@Service
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${app.ocr.base-url:}')")
public class HttpOcrClient implements OcrClient {

    private final RestClient restClient;

    // @Autowired is required: two constructors exist (this + test-only RestClient one).
    @Autowired
    public HttpOcrClient(
            @Value("${app.ocr.base-url}") String baseUrl,
            @Value("${app.ocr.timeout-ms:30000}") long timeoutMs
    ) {
        this(buildRestClient(baseUrl, timeoutMs));
    }

    /** Package-visible for unit tests with MockRestServiceServer. */
    HttpOcrClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public OcrResult recognize(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new ExtractionException("OCR image bytes are empty");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedImageResource(imageBytes));

        try {
            SidecarOcrResponse response = restClient.post()
                    .uri("/ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(SidecarOcrResponse.class);

            if (response == null) {
                throw new ExtractionException("OCR sidecar returned an empty body");
            }
            return toOcrResult(response);
        } catch (RestClientException exception) {
            throw new ExtractionException("OCR sidecar request failed", exception);
        }
    }

    private static RestClient buildRestClient(String baseUrl, long timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // Sync document processing waits on OCR; keep within the plan's ~30s budget.
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private static OcrResult toOcrResult(SidecarOcrResponse response) {
        String rawText = response.text() != null ? response.text() : "";
        List<OcrLine> lines = response.lines() == null
                ? List.of()
                : response.lines().stream()
                .filter(line -> line != null && line.text() != null && !line.text().isBlank())
                .map(line -> new OcrLine(line.text().strip(), line.bbox()))
                .toList();
        return new OcrResult(rawText, lines);
    }

    /**
     * Multipart part needs a filename so Spring sets Content-Disposition correctly.
     */
    private static final class NamedImageResource extends ByteArrayResource {
        private NamedImageResource(byte[] imageBytes) {
            super(imageBytes);
        }

        @Override
        public String getFilename() {
            return "image.bin";
        }
    }

    /** JSON body from POST /ocr on the sidecar. */
    record SidecarOcrResponse(String text, List<SidecarOcrLine> lines) {
    }

    record SidecarOcrLine(String text, BoundingBox bbox) {
    }
}

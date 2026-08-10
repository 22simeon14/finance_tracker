package com.financetracker.document;

import java.time.LocalDateTime;

/**
 * Main Responsibility: JSON response for the document review screen.
 *
 * Extends upload metadata with fileUrl (authenticated file stream path) and
 * nullable extraction proposals. Does not expose storagePath or userId.
 */
public record DocumentReviewResponse(
        Long id,
        String status,
        String originalFilename,
        String mimeType,
        Integer fileSizeBytes,
        LocalDateTime createdAt,
        String fileUrl,
        ExtractionResponse extraction
) {
}

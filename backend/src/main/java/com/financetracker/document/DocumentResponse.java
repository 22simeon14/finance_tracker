package com.financetracker.document;

import java.time.LocalDateTime;

/**
 * Main Responsibility: JSON response for document metadata (upload / get).
 *
 * Exposes id, status, filename, MIME, size, and createdAt —
 * not storagePath (server-only) or userId (implied by auth).
 */
public record DocumentResponse(
        Long id,
        String status,
        String originalFilename,
        String mimeType,
        Integer fileSizeBytes,
        LocalDateTime createdAt
) {
}

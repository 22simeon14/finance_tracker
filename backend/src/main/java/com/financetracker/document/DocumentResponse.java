package com.financetracker.document;

import java.time.LocalDateTime;

/**
 * Main Responsibility: Slim JSON row for the pending-documents inbox list.
 *
 * Exposes id, status, filename, MIME, createdAt, and fileUrl —
 * not storagePath (server-only), userId (implied by auth), or extraction.
 */
public record DocumentResponse(
        Long id,
        String status,
        String originalFilename,
        String mimeType,
        LocalDateTime createdAt,
        String fileUrl
) {
}

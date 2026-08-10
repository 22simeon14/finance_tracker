package com.financetracker.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Main Responsibility: Database access for DocumentExtraction entities.
 *
 * findByDocumentId supports upsert on retry (one extraction row per document).
 */
public interface DocumentExtractionRepository extends JpaRepository<DocumentExtraction, Long> {

    Optional<DocumentExtraction> findByDocumentId(Long documentId);
}

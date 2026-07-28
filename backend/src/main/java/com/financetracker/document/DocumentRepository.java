package com.financetracker.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Main Responsibility: Database access for Document entities.
 *
 * Owner-scoped lookups use id + userId so foreign documents never leak.
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByIdAndUserId(Long id, Long userId);
}

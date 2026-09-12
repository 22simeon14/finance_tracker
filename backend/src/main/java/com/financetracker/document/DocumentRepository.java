package com.financetracker.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Main Responsibility: Database access for Document entities.
 *
 * Owner-scoped lookups use id + userId so foreign documents never leak.
 * Pending inbox lists every non-SAVED row for the owner (newest first).
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByIdAndUserId(Long id, Long userId);

    /**
     * Pending inbox: all owned documents that are not yet SAVED
     * (UPLOADED, PROCESSING, REVIEW_REQUIRED, PROCESSING_FAILED, etc.).
     * Newest createdAt first so unfinished work surfaces at the top.
     */
    @Query("""
            SELECT d FROM Document d
            WHERE d.userId = :userId AND d.status <> 'SAVED'
            ORDER BY d.createdAt DESC
            """)
    List<Document> findPendingByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
}

package com.financetracker.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Main Responsibility: Database access for DocumentExtractionLine entities.
 *
 * findByExtractionIdOrderByPositionAsc loads review lines in proposal order.
 * deleteByExtractionId clears lines when headers are cleared or replaced.
 */
public interface DocumentExtractionLineRepository extends JpaRepository<DocumentExtractionLine, Long> {

    List<DocumentExtractionLine> findByExtractionIdOrderByPositionAsc(Long extractionId);

    void deleteByExtractionId(Long extractionId);
}

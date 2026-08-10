package com.financetracker.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Main Responsibility: JPA entity mapped to the "document_extractions" table.
 *
 * Holds OCR/mock proposed expense fields for one document (1:1 via document_id).
 * Schema is owned by SQL migrations (ddl-auto=none); this class only maps columns.
 * created_at / updated_at are set by JPA lifecycle callbacks, not by callers.
 */
@Entity
@Table(name = "document_extractions")
public class DocumentExtraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One extraction row per document; UNIQUE in DB enforces the 1:1 link.
    @Column(name = "document_id", nullable = false, unique = true)
    private Long documentId;

    @Column(name = "raw_ocr_text", columnDefinition = "TEXT")
    private String rawOcrText;

    @Column(name = "proposed_merchant", length = 255)
    private String proposedMerchant;

    @Column(name = "proposed_date")
    private LocalDate proposedDate;

    @Column(name = "proposed_amount", precision = 12, scale = 2)
    private BigDecimal proposedAmount;

    @Column(name = "proposed_currency", length = 3)
    private String proposedCurrency;

    @Column(name = "proposed_category_id")
    private Long proposedCategoryId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Set both timestamps when the row is first inserted. */
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    /** Refresh updated_at on every update. */
    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getRawOcrText() {
        return rawOcrText;
    }

    public void setRawOcrText(String rawOcrText) {
        this.rawOcrText = rawOcrText;
    }

    public String getProposedMerchant() {
        return proposedMerchant;
    }

    public void setProposedMerchant(String proposedMerchant) {
        this.proposedMerchant = proposedMerchant;
    }

    public LocalDate getProposedDate() {
        return proposedDate;
    }

    public void setProposedDate(LocalDate proposedDate) {
        this.proposedDate = proposedDate;
    }

    public BigDecimal getProposedAmount() {
        return proposedAmount;
    }

    public void setProposedAmount(BigDecimal proposedAmount) {
        this.proposedAmount = proposedAmount;
    }

    public String getProposedCurrency() {
        return proposedCurrency;
    }

    public void setProposedCurrency(String proposedCurrency) {
        this.proposedCurrency = proposedCurrency;
    }

    public Long getProposedCategoryId() {
        return proposedCategoryId;
    }

    public void setProposedCategoryId(Long proposedCategoryId) {
        this.proposedCategoryId = proposedCategoryId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

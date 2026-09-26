package com.financetracker.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Main Responsibility: JPA entity mapped to "document_extraction_lines".
 *
 * One proposed receipt line under a document_extractions row. Schema is owned
 * by SQL migrations (ddl-auto=none); this class only maps columns.
 */
@Entity
@Table(name = "document_extraction_lines")
public class DocumentExtractionLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "extraction_id", nullable = false)
    private Long extractionId;

    @Column(nullable = false, length = 255)
    private String description;

    // Optional; null when OCR/LLM did not provide a quantity.
    @Column(precision = 12, scale = 3)
    private BigDecimal quantity;

    // Optional unit price; null when unknown. Soft OCR mismatches vs amount are OK.
    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    // Line total (always required and positive).
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // Null when the parser slug did not match an active category.
    @Column(name = "category_id")
    private Long categoryId;

    // Stable order within one extraction (0-based from the proposal list).
    @Column(nullable = false)
    private Integer position;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExtractionId() {
        return extractionId;
    }

    public void setExtractionId(Long extractionId) {
        this.extractionId = extractionId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }
}

package com.financetracker.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Main Responsibility: JPA entity mapped to "expense_lines".
 *
 * One confirmed receipt line under an expenses row. Schema is owned by SQL
 * migrations (ddl-auto=none); this class only maps columns. Unapprove deletes
 * the parent expense; the database cascades these rows.
 */
@Entity
@Table(name = "expense_lines")
public class ExpenseLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(nullable = false, length = 255)
    private String description;

    // Optional; null when the review form left quantity blank.
    @Column(precision = 12, scale = 3)
    private BigDecimal quantity;

    // Optional unit price; null when unknown. May not equal amount / quantity.
    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    // Line total (always required and positive).
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // Null when the user did not pick a category for this line.
    @Column(name = "category_id")
    private Long categoryId;

    // Stable order within one expense (0-based from the approve list).
    @Column(nullable = false)
    private Integer position;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExpenseId() {
        return expenseId;
    }

    public void setExpenseId(Long expenseId) {
        this.expenseId = expenseId;
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

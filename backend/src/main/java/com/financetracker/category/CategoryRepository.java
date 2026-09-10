package com.financetracker.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Main Responsibility: Database access for Category entities.
 *
 * Lists only active categories, sorted by name (for forms and filters).
 * Approve uses findByIdAndIsActiveTrue so inactive ids are rejected.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByIsActiveTrueOrderByNameAsc();

    Optional<Category> findByIdAndIsActiveTrue(Long id);
}

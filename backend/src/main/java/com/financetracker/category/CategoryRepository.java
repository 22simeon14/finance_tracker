package com.financetracker.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Main Responsibility: Database access for Category entities.
 *
 * Lists only active categories, sorted by name (for forms and filters).
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByIsActiveTrueOrderByNameAsc();
}

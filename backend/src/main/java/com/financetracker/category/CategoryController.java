package com.financetracker.category;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Main Responsibility: HTTP endpoint to list active shared categories.
 *
 * GET /categories requires JWT (no permitAll in SecurityConfig). Categories are
 * seeded and shared — not scoped per user, so CurrentUser is not used.
 */
@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** Returns active categories only, ordered by name, mapped to the public DTO. */
    @GetMapping
    public List<CategoryResponse> list() {
        return categoryRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(category -> new CategoryResponse(
                        category.getId(),
                        category.getName(),
                        category.getSlug()))
                .toList();
    }
}

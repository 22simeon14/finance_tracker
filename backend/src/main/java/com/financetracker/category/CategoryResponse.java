package com.financetracker.category;

/**
 * Main Responsibility: JSON response for one category in GET /categories.
 *
 * Exposes only id, name, and slug — not isActive or createdAt.
 */
public record CategoryResponse(Long id, String name, String slug) {
}

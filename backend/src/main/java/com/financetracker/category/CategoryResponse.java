package com.financetracker.category;

/**
 * Main Responsibility: JSON response for one category in GET /categories.
 *
 * parentId is null for a top-level group and set for a leaf.
 * isActive and createdAt stay off this DTO.
 */
public record CategoryResponse(Long id, String name, String slug, Long parentId) {
}

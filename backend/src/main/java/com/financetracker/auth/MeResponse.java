package com.financetracker.auth;

/**
 * Main Responsibility: JSON response for GET /auth/me (current user id and email).
 */
public record MeResponse(Long id, String email) {
}

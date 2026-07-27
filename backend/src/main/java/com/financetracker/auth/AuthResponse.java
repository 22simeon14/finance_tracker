package com.financetracker.auth;

/**
 * Main Responsibility: JSON response after successful register or login (JWT string).
 */
public record AuthResponse(String token) {
}

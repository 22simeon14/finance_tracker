package com.financetracker.security;

/**
 * Main Responsibility: Hold the authenticated user's id and email in the SecurityContext.
 *
 * Set by JwtAuthFilter after a valid JWT; read by CurrentUser in controllers.
 */
public record UserPrincipal(Long id, String email) {
}

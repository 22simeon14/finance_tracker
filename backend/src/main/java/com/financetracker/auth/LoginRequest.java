package com.financetracker.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Main Responsibility: Validated JSON body for POST /auth/login.
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}

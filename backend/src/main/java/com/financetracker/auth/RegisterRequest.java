package com.financetracker.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Main Responsibility: Validated JSON body for POST /auth/register.
 *
 * Password length is enforced here (8–100) before AuthService runs.
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password
) {
}

package com.financetracker.auth;

import com.financetracker.security.JwtService;
import com.financetracker.user.User;
import com.financetracker.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Main Responsibility: Register and login business logic, then issue a JWT.
 *
 * Passwords are stored only as BCrypt hashes. Email is normalized (trim + lowercase)
 * before lookup so "User@Mail.com" and "user@mail.com" match the same account.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = new User();
        user.setEmail(email);
        // Never store the plain password — only the BCrypt hash.
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        return new AuthResponse(jwtService.createToken(user.getId(), user.getEmail()));
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> invalidCredentials());

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        return new AuthResponse(jwtService.createToken(user.getId(), user.getEmail()));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    /**
     * Same message for "user missing" and "wrong password" so callers cannot tell them apart.
     */
    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}

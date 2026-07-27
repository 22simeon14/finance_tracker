package com.financetracker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Main Responsibility: Turn a Bearer JWT into a Spring Security authentication.
 *
 * Reads the Authorization Bearer token, validates it via JwtService, then stores
 * a UserPrincipal (id + email) and ROLE_USER in the SecurityContext for this request.
 * Invalid or missing tokens do not block the filter chain here — SecurityConfig
 * still rejects protected routes if no authentication was set.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")) {
            // "Bearer " is 7 characters; the rest is the raw JWT.
            String token = header.substring(7);

            if (jwtService.isValid(token)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                Long userId = jwtService.getUserId(token);
                String email = jwtService.getEmail(token);

                UserPrincipal principal = new UserPrincipal(userId, email);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}

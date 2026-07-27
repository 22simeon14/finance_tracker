package com.financetracker.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Main Responsibility: Public liveness check for the app and database.
 *
 * GET /health runs SELECT 1. Returns 200 when DB is up, 503 with status "degraded" when not.
 * Used by the frontend home page and by operators / Docker health checks.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> body = new LinkedHashMap<>();

        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            body.put("status", "ok");
            body.put("database", "up");
            return ResponseEntity.ok(body);
        } catch (Exception ex) {
            // App process may still be running; report DB failure without crashing.
            body.put("status", "degraded");
            body.put("database", "down");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}

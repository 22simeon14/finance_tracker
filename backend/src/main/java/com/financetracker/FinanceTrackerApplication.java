package com.financetracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Responsibility: Start the Spring Boot backend application.
 *
 * This is the entry point. Spring scans this package and sub-packages
 * for components, configs, and controllers.
 */
@SpringBootApplication
public class FinanceTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceTrackerApplication.class, args);
    }
}

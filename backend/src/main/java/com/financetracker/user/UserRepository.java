package com.financetracker.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Main Responsibility: Database access for User entities.
 *
 * Spring Data implements find/exists by email (case-insensitive) from the method names.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}

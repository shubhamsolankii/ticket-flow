package com.ticketflow.authservice.repository;

import com.ticketflow.authservice.entity.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * AuthUserRepository — data access for the auth_users table.
 *
 * Spring Data generates all implementations at startup via JDK dynamic proxies.
 * No SQL is written here. The method names ARE the query specification.
 *
 * Feature 1 (Register with Email) uses:
 *   existsByEmail → duplicate check before insert
 *   save          → inherited from JpaRepository, persists the new AuthUser
 */
@Repository
public interface AuthUserRepository extends JpaRepository<AuthUser, String> {

    /**
     * Translates to: SELECT COUNT(*) > 0 FROM auth_users WHERE email = ?
     *
     * Used in AuthService.register() before creating a new AuthUser.
     * If true → throw DuplicateEmailException → caller gets HTTP 409.
     *
     * Why not findByEmail here?
     * We don't need the AuthUser object for a duplicate check.
     * existsByEmail issues a COUNT query — no row hydration, no object
     * allocation. Correct tool for a boolean existence check.
     */
    boolean existsByEmail(String email);
}
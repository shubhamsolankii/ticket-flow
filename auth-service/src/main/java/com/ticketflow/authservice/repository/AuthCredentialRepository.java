package com.ticketflow.authservice.repository;

import com.ticketflow.authservice.entity.AuthCredential;
import com.ticketflow.authservice.entity.AuthCredential.CredentialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * AuthCredentialRepository — data access for the auth_credentials table.
 *
 * Feature 1 (Register with Email) uses:
 *   existsByEmailAndCredentialType → credential-level duplicate check
 *   save                           → inherited from JpaRepository
 *
 * Methods for Login, Password Reset, OAuth2 are added in their
 * respective features. Nothing is added here speculatively.
 */
@Repository
public interface AuthCredentialRepository extends JpaRepository<AuthCredential, String> {

    /**
     * Translates to:
     *   SELECT COUNT(*) > 0 FROM auth_credentials
     *   WHERE email = ? AND credential_type = ?
     *
     * Hits the composite index idx_auth_credentials_email_type directly.
     * One index scan. No table access. Sub-millisecond at any table size.
     *
     * Called in AuthService.register() after the AuthUser-level email check.
     * Guards against the race condition described in design decisions above,
     * and distinguishes duplicate registration from "add new login method"
     * flows in future features.
     */
    boolean existsByEmailAndCredentialType(String email, CredentialType credentialType);
}
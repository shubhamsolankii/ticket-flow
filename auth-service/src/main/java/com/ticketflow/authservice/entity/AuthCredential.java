package com.ticketflow.authservice.entity;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * AuthCredential — one row per login method per user.
 *
 * A single AuthUser can have multiple AuthCredential rows:
 *   EMAIL_PASSWORD → email + bcrypt hash
 *   MOBILE_OTP     → mobile number (OTP itself lives in Redis, not here)
 *   GOOGLE         → provider user ID from Google's token
 *   GITHUB         → provider user ID from GitHub's token
 *
 * For Feature 1 (Register with Email), only EMAIL_PASSWORD is created.
 * Other types are created in their respective features.
 */
@Entity
@Table(
        name = "auth_credentials",
        indexes = {
                // Primary lookup for email login: "find credential by email + type"
                // Composite index: both columns together identify one unique credential.
                @Index(name = "idx_auth_credentials_email_type",
                        columnList = "email, credential_type",
                        unique = true),

                // Lookup by user_id: "fetch all credentials for this user"
                // Used when checking if a user already has a specific credential type.
                @Index(name = "idx_auth_credentials_user_id",
                        columnList = "user_id")
        }
)
public class AuthCredential {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 26)
    private String id;

    /**
     * FK to auth_users.id — which user owns this credential.
     * LAZY fetch: we never need the full AuthUser when checking credentials.
     * We store userId as a direct column for queries that only need the FK value
     * (avoids Hibernate proxy initialization just to read the ID).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AuthUser authUser;

    /**
     * Denormalized FK value — same as authUser.id.
     * Stored explicitly so repository queries can filter by userId
     * without triggering a JOIN to auth_users.
     * insertable = false, updatable = false: Hibernate manages this
     * column via the @ManyToOne above; this field is read-only.
     */
    @Column(name = "user_id", nullable = false, updatable = false,
            insertable = false, length = 26)
    private String userId;

    /**
     * Which login method this credential represents.
     * EnumType.STRING: stores "EMAIL_PASSWORD", not 0.
     * updatable = false: credential type never changes after creation.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, updatable = false, length = 20)
    private CredentialType credentialType;

    /**
     * The identifier used to look up this credential.
     * For EMAIL_PASSWORD: the user's email address.
     * For MOBILE_OTP: the user's mobile number (E.164 format: +919876543210).
     * For GOOGLE/GITHUB: the provider's user ID (not the email).
     *
     * Indexed together with credential_type — see composite index above.
     */
    @Column(name = "email", length = 255)
    private String email;

    /**
     * BCrypt hash of the user's password. Cost factor 12 (system design Section 10).
     * NULL for OAuth2 and mobile credential types — they have no password.
     * NEVER store plaintext. AuthService calls BCryptPasswordEncoder.encode() before
     * setting this field. The raw password must never reach this entity.
     */
    @Column(name = "password_hash", length = 72)
    private String passwordHash;

    /**
     * Count of consecutive failed login attempts for this credential.
     * Incremented on each wrong password. Reset to 0 on successful login.
     * When this reaches 5, lockedUntil is set to now + 30 minutes.
     * Logic lives in AuthService (Login feature — not implemented yet).
     */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /**
     * If set, this credential is locked until this timestamp.
     * NULL means not locked. Checked on every login attempt.
     * Set by AuthService when failedLoginAttempts reaches threshold.
     */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // =========================================================================
    // CREDENTIAL TYPE ENUM
    // Defined as inner enum — belongs conceptually to AuthCredential.
    // If it grows large or needs to be shared, promote to a top-level class.
    // =========================================================================

    public enum CredentialType {
        EMAIL_PASSWORD,
        MOBILE_OTP,
        GOOGLE,
        GITHUB
    }

    // =========================================================================
    // JPA LIFECYCLE CALLBACKS
    // =========================================================================

    @PrePersist
    protected void onCreate() {
        this.id = UlidCreator.getMonotonicUlid().toString();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.failedLoginAttempts = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // =========================================================================
    // EQUALS & HASHCODE — based on id (ULID) only
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthCredential other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }

    // =========================================================================
    // GETTERS & SETTERS
    // No setter for id, userId, credentialType, createdAt — immutable after persist.
    // =========================================================================

    public String getId() { return id; }

    public AuthUser getAuthUser() { return authUser; }
    public void setAuthUser(AuthUser authUser) { this.authUser = authUser; }

    public String getUserId() { return userId; }

    public CredentialType getCredentialType() { return credentialType; }
    public void setCredentialType(CredentialType credentialType) {
        this.credentialType = credentialType;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
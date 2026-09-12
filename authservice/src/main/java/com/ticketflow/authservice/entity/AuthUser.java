package com.ticketflow.authservice.entity;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * AuthUser — the root identity record for every user in TicketFlow.
 *
 * Owns: who this person is (name, email copy for display, status, timestamps).
 * Does NOT own: how they authenticate. That lives in AuthCredential.
 *
 * One AuthUser can have multiple AuthCredentials:
 *   → one for email+password
 *   → one for Google OAuth2
 *   → one for mobile OTP
 * This is why credentials are a separate entity, not fields on this table.
 */
@Entity
@Table(
        name = "auth_users",
        indexes = {
                // email is the primary lookup key for display and deduplication checks.
                // auth-service stores a copy here; the canonical profile lives in user-service.
                @Index(name = "idx_auth_users_email", columnList = "email", unique = true)
        }
)
public class AuthUser {

    /**
     * ULID — lexicographically sortable, B-tree friendly.
     * Generated in @PrePersist, never in the constructor.
     * See design decisions above for why.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 26)
    private String id;

    /**
     * Email stored here for two reasons:
     * 1. Deduplication check at registration time (is this email already taken?)
     * 2. Passed in the Kafka user.registered event so user-service can create profile
     *
     * This is NOT the source of truth for the user's email in the broader system —
     * user-service owns the profile. This is auth-service's local copy for its own use.
     */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Whether this account is active. Defaults true on creation.
     * Set to false for: manual admin ban, account deletion request.
     * Checked on every login attempt in AuthService.
     *
     * Separate from credential-level locking (e.g. too many failed password
     * attempts) which lives on AuthCredential. This is account-level status.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Whether the user has verified their email.
     * False on registration. Set to true when the user clicks the
     * verification link (Feature: Email Verification — future feature).
     * Stored here because it's an account-level property, not a
     * credential-level property.
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /**
     * Managed by @PrePersist. Instant (not LocalDateTime) because:
     * - Instant is always UTC — no timezone ambiguity across services
     * - Maps to TIMESTAMPTZ in PostgreSQL — preserves timezone info
     * - Correct type for distributed systems where nodes may be in different zones
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // =========================================================================
    // JPA LIFECYCLE CALLBACKS
    // =========================================================================

    @PrePersist
    protected void onCreate() {
        // UlidCreator.getMonotonicUlid(): within the same millisecond,
        // the random component increments monotonically.
        // This guarantees strict ordering even for rapid-fire inserts.
        this.id = UlidCreator.getMonotonicUlid().toString();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;  // same instant on creation
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // =========================================================================
    // EQUALS & HASHCODE
    // Based on id (ULID) only — the business key.
    // NOT based on all fields — see design decisions above for why.
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthUser other)) return false;
        // id can be null before @PrePersist fires (i.e. before first save).
        // Two unsaved entities are never equal.
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        // Fixed constant for unsaved entities (id == null).
        // This is the JPA-safe pattern — see Vlad Mihalcea's recommendations.
        return id != null ? id.hashCode() : getClass().hashCode();
    }

    // =========================================================================
    // GETTERS & SETTERS
    // No setter for id, createdAt — they are immutable after persist.
    // =========================================================================

    public String getId() { return id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
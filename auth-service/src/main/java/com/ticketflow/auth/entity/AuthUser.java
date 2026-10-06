package com.ticketflow.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.security.AuthProvider;
import java.time.Instant;

@Entity
@Table(name = "auth_users", schema = "auth")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser{

    @Id
    @Column( name = "id", nullable = false, updatable = false, length = 26)
    private String id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;


    public boolean isActive(){
        return UserStatus.ACTIVE.equals(this.status) && this.deletedAt == null;
    }

    public boolean isLoginBlocked() {
        return UserStatus.SUSPENDED.equals(this.status)
                || UserStatus.DEACTIVATED.equals(this.status)
                || this.deletedAt != null;
    }

    public enum UserStatus {
        PENDING_VERIFICATION,
        ACTIVE,
        SUSPENDED,
        DEACTIVATED
    }

    public enum AuthProvider {
        EMAIL,
        MOBILE,
        GOOGLE,
        GITHUB
    }

}

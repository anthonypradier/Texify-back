package com.texify.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;


/**
 * JPA entity representing a Texify user.
 * <p>
 * This class is a pure domain/persistence object. It does <em>not</em> implement
 * {@code UserDetails}: Spring Security concerns are handled by
 * {@link com.texify.backend.service.UserDetailsServiceImpl}, which builds a
 * transient {@code UserDetails} from this entity on each authentication.
 * </p>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique email address — used as the login identifier. */
    @Column(unique = true, nullable = false)
    private String email;

    /** BCrypt-hashed password. Null for OAuth2-only accounts (GOOGLE, GITHUB). */
    @Column
    private String password;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    /**
     * Role granted to this user.
     * Stored as a plain string for readability and forward compatibility.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** How the account was originally created. */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    /**
     * Whether the account is active.
     * Remains {@code false} until the user verifies their email address.
     * OAuth2 accounts are enabled immediately (provider already verified the email).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    /** Set once on first INSERT, never updated afterwards. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Refreshed on every UPDATE. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Initialises audit timestamps before the first INSERT. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /** Refreshes {@code updatedAt} before every UPDATE. */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

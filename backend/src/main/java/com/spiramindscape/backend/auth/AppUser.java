package com.spiramindscape.backend.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Persistent user account, created on first Google sign-in.
 * Identity key is {@code googleSub} (the stable Google OIDC {@code sub} claim),
 * not the email address (email can change/transfer across accounts).
 *
 * <p>{@link Serializable} because it travels inside the session principal
 * ({@link AppUserOidcUser}), and sessions are JDK-serialized into PostgreSQL
 * by spring-session-jdbc.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
public class AppUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Google OIDC {@code sub} — stable, never changes. */
    @Column(name = "google_sub", unique = true, nullable = false)
    private String googleSub;

    /** User's email from Google (may change; kept for display only). */
    @Column(name = "email", unique = true, nullable = false)
    private String email;

    /** Display name from Google profile. */
    @Column(name = "name")
    private String name;

    /** Avatar URL from Google (may be null). */
    @Column(name = "picture_url")
    private String pictureUrl;

    /** Role for future-proofing; default is USER. */
    @Column(name = "role", nullable = false)
    private String role = "USER";

    /**
     * Google OAuth refresh token, AES-256-GCM encrypted (see {@code EncryptionService}).
     * Null until the user grants offline access. Used to mint fresh Drive access
     * tokens without a re-login. Never exposed through the API.
     */
    @Column(name = "enc_refresh_token", columnDefinition = "TEXT")
    private String encRefreshToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Updated each time the user signs in. */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

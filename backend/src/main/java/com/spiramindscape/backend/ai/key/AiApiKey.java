package com.spiramindscape.backend.ai.key;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Stores an encrypted API key for one AI provider, per user.
 *
 * <p>{@code appUserId} is nullable until Google OAuth is merged; the service
 * layer uses a dev stub (user id = 1) when no authentication context exists.
 * Once auth is integrated, this column becomes NOT NULL and the unique
 * constraint {@code (app_user_id, provider)} enforces one active key per
 * provider per user.
 */
@Entity
@Table(name = "ai_api_keys")
@Getter
@Setter
public class AiApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning user. Nullable while auth is absent from this branch. */
    @Column(name = "app_user_id")
    private Long appUserId;

    /** Provider identifier — one of {@code ANTHROPIC}, {@code OPENAI}, {@code MISTRAL}. */
    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    /**
     * Optional model override for this key.
     * {@code null} means the provider's default model is used.
     */
    @Column(name = "model", length = 64)
    private String model;

    /** AES-256-GCM encrypted API key (IV prepended, Base64-encoded). */
    @Column(name = "enc_key", nullable = false, columnDefinition = "TEXT")
    private String encKey;

    /**
     * Last 4 characters of the original key, stored in plaintext for display.
     * Example: {@code ••••1234}
     */
    @Column(name = "key_hint", nullable = false, length = 16)
    private String keyHint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

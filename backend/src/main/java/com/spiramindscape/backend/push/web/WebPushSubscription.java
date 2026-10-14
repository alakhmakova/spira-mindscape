package com.spiramindscape.backend.push.web;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One browser's Web Push subscription (V41): where to send ({@code endpoint}, on a push service
 * from {@link WebPushEndpoints}'s allowlist) and the keys to encrypt for it. The endpoint is
 * unique: the same browser signing in as someone else moves the row rather than duplicating it,
 * as {@code device_token} does for the Android app.
 */
@Entity
@Table(name = "web_push_subscription")
@Getter
@Setter
public class WebPushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 1000)
    private String endpoint;

    /** The browser's P-256 public key, base64url. */
    @Column(nullable = false, length = 200)
    private String p256dh;

    /** The browser's 16-byte auth secret, base64url. */
    @Column(nullable = false, length = 100)
    private String auth;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        if (this.lastSeenAt == null) {
            this.lastSeenAt = now;
        }
    }
}

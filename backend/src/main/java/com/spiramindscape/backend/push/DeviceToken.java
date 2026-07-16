package com.spiramindscape.backend.push;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A registered device's FCM token, owned by one {@link com.spiramindscape.backend.auth.AppUser}.
 *
 * <p>Push notifications are addressed to these tokens. The token is unique per app install, so
 * {@code token} carries a unique constraint (V15): the same device signing in as a different
 * user moves the row's {@code userId} rather than creating a duplicate.
 *
 * <p>{@code userId} is stored as a plain id (not a {@code @ManyToOne}) — device rows are always
 * queried by owner and never need the loaded user graph.
 */
@Entity
@Table(name = "device_token")
@Getter
@Setter
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owner. Rows are only ever read/written scoped to the current user. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The FCM registration token this device reported. Unique across all installs. */
    @Column(name = "token", unique = true, nullable = false)
    private String token;

    /** Device platform; only "android" today. */
    @Column(name = "platform", nullable = false)
    private String platform = "android";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Refreshed every time the device re-registers, so stale tokens can be pruned later. */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastSeenAt = now;
    }
}

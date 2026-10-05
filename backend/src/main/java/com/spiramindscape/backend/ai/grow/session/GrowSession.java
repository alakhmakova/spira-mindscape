package com.spiramindscape.backend.ai.grow.session;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A GROW coaching session that is still running, held server-side so it belongs to the user
 * rather than to the device it was started on.
 *
 * <p>Short-lived by design: written while the session runs, deleted the moment it ends. What
 * survives a session is the record the user keeps ({@code goal.ai_memory}) and whatever they
 * approved into the goal — never this row.
 *
 * <p>{@link #content} is the client's own JSON (session length, when it ends, its messages) and is
 * opaque here, exactly like {@code ai_chat_transcript.content}: both surfaces write the same shape,
 * and the schema does not have to move when that shape does.
 */
@Entity
@Table(name = "ai_grow_session")
@Getter
@Setter
public class GrowSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_user_id", nullable = false)
    private Long appUserId;

    /** Never null — a session always sits inside a goal. */
    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = Instant.now();
    }
}

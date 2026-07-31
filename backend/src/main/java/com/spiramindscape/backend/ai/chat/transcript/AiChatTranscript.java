package com.spiramindscape.backend.ai.chat.transcript;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A user's persisted regular-chat transcript for one scope — a specific goal, or
 * the global (all-goals) chat when {@link #goalId} is null. Stored so the
 * conversation follows the user across devices (BUG-018). One row per
 * {@code (appUserId, goalId)}; one global row per user.
 */
@Entity
@Table(name = "ai_chat_transcript")
@Getter
@Setter
public class AiChatTranscript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_user_id", nullable = false)
    private Long appUserId;

    /** Nullable — the global (all-goals) chat has no goal. */
    @Column(name = "goal_id")
    private Long goalId;

    /** JSON array of chat messages the client renders (no attachment file bytes). */
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

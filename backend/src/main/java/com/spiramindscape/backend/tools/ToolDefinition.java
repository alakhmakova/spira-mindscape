package com.spiramindscape.backend.tools;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A Personal Tool definition: a small user-facing widget (job tracker, weight
 * log, countdown, …) described by a JSON schema of approved UI primitives.
 * Filled with {@link ToolRecord}s and drawn by one generic frontend renderer.
 *
 * <p>{@code schemaJson} is TEXT, not JSONB (H2-testable; we never query inside
 * it). The schema is validated in Java by {@code ToolSchemaValidator}.
 */
@Entity
@Table(name = "tool_definitions")
@Getter
@Setter
public class ToolDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_user_id", nullable = false)
    private Long appUserId;

    /** Null = global (belongs to the user, not a specific goal). */
    @Column(name = "goal_id")
    private Long goalId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "schema_json", nullable = false, columnDefinition = "TEXT")
    private String schemaJson;

    /** Where the tool renders: {@code goal} | {@code all_goals} | {@code tools}. */
    @Column(nullable = false, length = 16)
    private String placement;

    /** {@code ai} | {@code user}. */
    @Column(name = "created_by", nullable = false, length = 8)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

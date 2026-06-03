package com.spiramindscape.backend.ai.proposal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * An AI-generated change proposal that must be explicitly approved by the user
 * before it is applied to goal data.
 *
 * <p>Lifecycle: {@code PENDING → APPROVED | REJECTED}
 */
@Entity
@Table(name = "ai_proposals")
@Getter
@Setter
public class AiProposal {

    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_user_id")
    private Long appUserId;

    /** Nullable — proposals may be global (not tied to a specific goal). */
    @Column(name = "goal_id")
    private Long goalId;

    /**
     * Machine-readable proposal type.
     * Examples: {@code ADD_TARGET}, {@code UPDATE_DESCRIPTION}, {@code ADD_RESOURCE_NOTE},
     *           {@code SELECT_OPTION}, {@code ADD_REALITY_ITEM}.
     */
    @Column(name = "type", nullable = false, length = 64)
    private String type;

    /**
     * JSON payload whose structure depends on {@link #type}.
     * The frontend deserializes this to render the proposal card and, on
     * approval, sends it back to the appropriate mutation endpoint.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

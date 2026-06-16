package com.spiramindscape.backend.tools;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One entry in a {@link ToolDefinition} — a row of the user's data, shaped to
 * the tool's schema. Stored as TEXT JSON (see {@link ToolDefinition}).
 */
@Entity
@Table(name = "tool_records")
@Getter
@Setter
public class ToolRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tool_def_id", nullable = false)
    private Long toolDefId;

    @Column(name = "data_json", nullable = false, columnDefinition = "TEXT")
    private String dataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

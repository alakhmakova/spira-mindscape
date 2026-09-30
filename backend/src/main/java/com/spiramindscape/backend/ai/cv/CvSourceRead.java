package com.spiramindscape.backend.ai.cv;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One primary source the job analysis (or the writer, in chat) actually read.
 *
 * <p>The ledger is what "covered" is checked against: a topic may only count as backed by
 * the user's work when a source other than her profile was read and cited. The profile is
 * a map of where to look, not the evidence (owner, 2026-09-15).
 */
@Entity
@Table(name = "cv_source_read",
        uniqueConstraints = @UniqueConstraint(name = "uq_cv_source_read",
                columnNames = {"application_id", "source_key"}))
@Getter
@Setter
public class CvSourceRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    /** {@code resource:123}, {@code repo:owner/name:path}, {@code repo-facts:owner/name}. */
    @Column(name = "source_key", nullable = false, length = 600)
    private String sourceKey;

    @Column(length = 300)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int chars;

    @Column(nullable = false)
    private boolean ok = true;

    @Column(nullable = false, length = 16)
    private String origin = "analysis";

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        fetchedAt = Instant.now();
    }
}

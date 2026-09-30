package com.spiramindscape.backend.ai.cv;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One requirement extracted from the posting, and whatever the user was able to
 * put behind it.
 *
 * <p>This row is the mechanism the whole feature rests on. The requirement list
 * lives here rather than in the model's context, and the server hands the model
 * one row at a time; "never dump the list on the user" is therefore a property of
 * the system rather than a request the model is free to drift away from.
 *
 * <p>It also makes the closing disclosure honest: "these points from the advert
 * are not in your CV, because you could not evidence them" is a query over
 * {@link Status#NO_EVIDENCE}, not something a model has to recall twenty turns later.
 */
@Entity
@Table(name = "cv_requirement")
@Getter
@Setter
public class CvRequirement {

    /** Which of the four groups a requirement falls in. */
    public enum Group {
        /** A concrete skill, tool or qualification. */
        HARD,
        /** A way of working or relating to people. */
        SOFT,
        /**
         * What the company says it is like. Treated as a requirement on purpose: a
         * candidate who does not share it has no reason to apply, and it is the
         * material the cover letter's motivation is built from.
         */
        CULTURE,
        /**
         * The model could not place it. <b>A real group, not a bin</b> — a requirement
         * must never be lost because it resisted classification, so these are split
         * into must/nice and worked through beside {@link #HARD}.
         */
        UNCLASSIFIED;

        public static Group from(String raw) {
            if (raw != null) {
                for (Group g : values()) if (g.name().equalsIgnoreCase(raw.strip())) return g;
            }
            return UNCLASSIFIED;
        }
    }

    /** Whether the posting treats it as required or as a bonus. */
    public enum Weight {
        MUST, NICE;

        public static Weight from(String raw) {
            return raw != null && NICE.name().equalsIgnoreCase(raw.strip()) ? NICE : MUST;
        }
    }

    /** Where this requirement has got to in the interview. */
    public enum Status {
        PENDING,
        /** The user gave a story that stands behind it. */
        ANSWERED,
        /**
         * The user cannot back it. Kept rather than deleted — this is what the
         * user is told about at the end, so they get the chance to remember
         * something rather than have the gap pass in silence.
         */
        NO_EVIDENCE,
        /** Deliberately passed over, e.g. a "nice to have" the user does not want to spend time on. */
        SKIPPED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    /**
     * The requirement in the posting's own words.
     *
     * <p>Stored verbatim for exactly one purpose: quoting it back during the
     * interview, and showing the user the same demand appearing in several
     * paragraphs. It must never reach the CV in these words — copying the advert's
     * prose is the defect this whole feature exists to prevent.
     */
    @Column(name = "req_text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "req_group", nullable = false, length = 16)
    private Group group = Group.UNCLASSIFIED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Weight weight = Weight.MUST;

    /**
     * Requirements the advert states more than once in different words share a
     * cluster. Repetition within a single posting is the employer's own emphasis,
     * and it is the best priority signal available when there is only one advert to
     * read.
     */
    @Column(length = 32)
    private String cluster;

    /** Which paragraph of the advert it came from. */
    @Column(name = "source_paragraph")
    private Integer sourceParagraph;

    /** Position in the interview queue: clusters first, then must + soft, then nice. */
    @Column(name = "queue_index", nullable = false)
    private int queueIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(length = 300)
    private String company;

    /** One line of what the story was. The full story lives in the story-bank note. */
    @Column(columnDefinition = "TEXT")
    private String gist;

    /** The confirmed one-liner this becomes in the CV, played back before it is used. */
    @Column(name = "cv_line", columnDefinition = "TEXT")
    private String cvLine;

    /** Traits the story demonstrated — recorded because a story showed them. */
    @Column(length = 500)
    private String traits;

    /**
     * Which story in the user's story bank this requirement is answered by.
     *
     * <p>A short handle the writer coins, so the table points INTO the bank instead of
     * holding a second copy of the story. It also makes reuse visible: the same handle
     * against several unrelated demands is the blind reuse the whole feature exists to
     * prevent, and it shows at a glance where duplicate free-text would not.
     */
    @Column(name = "story_handle", length = 64)
    private String storyHandle;

    @Column(name = "no_evidence_reason", columnDefinition = "TEXT")
    private String noEvidenceReason;

    // ── The topic this row is one quote of, and what the job analysis found ──
    // Rows sharing `cluster` are one topic; its analysis is stored on each of them.

    /** competence | core | requirement | trait | extra — see V26__cv_requirement_map.sql. */
    @Column(length = 16)
    private String category;

    /** yes | no | partial — the user's own verdict on a competence. */
    @Column(name = "user_verdict", length = 16)
    private String userVerdict;

    /** What the user said: the checklist comment, or the evidence for a requirement or a trait. */
    @Column(name = "user_answer", columnDefinition = "TEXT")
    private String userAnswer;

    /** The advert's line that implies a trait, so the question carries its context. */
    @Column(columnDefinition = "TEXT")
    private String context;

    /** extra only: letter (motivation) | flag (raise it with the user now). */
    @Column(name = "extra_use", length = 16)
    private String extraUse;

    @Column(length = 120)
    private String topic;

    @Column(columnDefinition = "TEXT")
    private String demand;

    /** 1 core · 2 important · 3 bonus. */
    @Column
    private Integer priority;

    /** covered | partial | gap — null until assessed. */
    @Column(length = 16)
    private String coverage;

    /** experience | credential */
    @Column(name = "claim_kind", length = 16)
    private String claimKind;

    /** What in the profile pointed here. A lead, never the evidence. */
    @Column(columnDefinition = "TEXT")
    private String lead;

    /** JSON array of cv_source_read keys. */
    @Column(columnDefinition = "TEXT")
    private String sources;

    @Column(columnDefinition = "TEXT")
    private String finding;

    @Column(name = "draft_line", columnDefinition = "TEXT")
    private String draftLine;

    @Column(columnDefinition = "TEXT")
    private String missing;

    @Column(columnDefinition = "TEXT")
    private String question;

    /** source (written from her documents) | user (from her answer) */
    @Column(length = 8)
    private String origin;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

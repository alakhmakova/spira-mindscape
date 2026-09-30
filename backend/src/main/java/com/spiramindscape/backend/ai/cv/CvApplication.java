package com.spiramindscape.backend.ai.cv;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One application to one vacancy: the posting, its deconstructed requirements, and
 * the notes the work produces.
 *
 * <p><b>Scoped to a vacancy, not to a goal.</b> A goal like "find a QA job" holds
 * many of these; the profile and story-bank notes are shared across them and
 * everything else belongs to this one application.
 *
 * <p>Unlike {@code GrowSession}, whose {@code content} is opaque client JSON, the
 * server reads every field here. It has to: feeding the model exactly one
 * requirement per turn is what stops the assistant dumping the list and writing a
 * CV out of the posting's own vocabulary, and that scheduling cannot live in the
 * client.
 */
@Entity
@Table(name = "cv_application")
@Getter
@Setter
public class CvApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_user_id", nullable = false)
    private Long appUserId;

    /** Never null — an application always sits inside a goal. */
    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "vacancy_url", length = 2000)
    private String vacancyUrl;

    @Column(name = "vacancy_text", nullable = false, columnDefinition = "TEXT")
    private String vacancyText;

    /**
     * The language the CV and the letter are written in — the posting's, which is
     * usually not the language the conversation runs in.
     */
    @Column(name = "vacancy_language", length = 16)
    private String vacancyLanguage;

    /** The addressee, when the ad names one. */
    @Column(name = "contact_name", length = 300)
    private String contactName;

    /**
     * Null until the {@code vacancy} phase settles it. It decides where motivation
     * goes: the letter, or SAMMANFATTNING when there is no letter.
     */
    @Column(name = "letter_required")
    private Boolean letterRequired;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CvPhase phase = CvPhase.ANALYSIS;

    @Column(name = "cursor_index", nullable = false)
    private int cursorIndex = 0;

    @Column(name = "profile_note_id")
    private Long profileNoteId;

    @Column(name = "stories_note_id")
    private Long storiesNoteId;

    @Column(name = "cv_note_id")
    private Long cvNoteId;

    @Column(name = "letter_note_id")
    private Long letterNoteId;

    @Column(name = "briefing_note_id")
    private Long briefingNoteId;

    /**
     * The conversation, as the client's own JSON.
     *
     * <p>The server never reads inside it — both surfaces agree the shape between
     * themselves, exactly as {@code ai_grow_session.content} does. It is here so a sitting
     * belongs to the user rather than to the browser it was typed in; without it an
     * application opened on another device had every answer and no conversation.
     */
    @Column(columnDefinition = "TEXT")
    private String transcript;

    /** The guided process — see V25__cv_guided_process.sql for what each column is for. */
    @Column(name = "role_title", length = 300)
    private String roleTitle;

    @Column(length = 300)
    private String location;

    /** What the advert is really about, in its own words — leads the summary and the letter. */
    @Column(name = "core_message", columnDefinition = "TEXT")
    private String coreMessage;

    /** The intake form is confirmed; the evidence collection can start. */
    @Column(name = "intake_confirmed_at")
    private java.time.Instant intakeConfirmedAt;

    @Column(name = "company_name", length = 300)
    private String companyName;

    @Column(name = "analysis_state", nullable = false, length = 16)
    private String analysisState = "none";

    /**
     * When the analysis was reported to the user — the counts, the core message, the map.
     *
     * <p>Its own field rather than a side-effect of {@link #announcedStep}, because the two are
     * not the same event any more: an advert carrying a condition the user has to answer now (a
     * background check, a permit, a stated number of years) is reported and then <b>waits</b>, so
     * step 2 is announced on a later turn and the summary must not be repeated when it is.
     */
    @Column(name = "analysis_reported_at")
    private Instant analysisReportedAt;

    @Column(name = "profile_confirmed_at")
    private Instant profileConfirmedAt;

    @Column(name = "announced_step")
    private Integer announcedStep;

    @Column(name = "analysis_note_id")
    private Long analysisNoteId;

    /**
     * The vacancy map this application's advert was read into — a {@code vacancy} resource, not a
     * note. It is what the user opens and edits; see {@code specs/2026-09-17-vacancy-map/}.
     */
    @Column(name = "map_resource_id")
    private Long mapResourceId;

    /**
     * The advert's hard conditions, one per line — a permit, a mandatory background check, a stated
     * number of years. They decide whether she can apply at all, so they are raised with her before
     * the map is worked on, and the summary waits for her answer.
     */
    @Column(name = "advert_conditions", columnDefinition = "TEXT")
    private String advertConditions;

    @Column(name = "analysis_note_rendered_at")
    private Instant analysisNoteRenderedAt;

    @Column(name = "conversation_language", length = 16)
    private String conversationLanguage;

    @Column(name = "transcript_revision", nullable = false)
    private long transcriptRevision = 0;

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

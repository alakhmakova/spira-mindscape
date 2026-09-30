-- ── The CV writer as a guided process ────────────────────────────────────────
-- specs/2026-09-15-cv-guided-process/. The owner's first real session (application 24)
-- was "chaos": no visible steps, the advert never actually analysed (0 requirements while
-- the writer claimed otherwise), questions about things her profile already answered.
-- The server now runs the job analysis itself and tells her, step by step, where she is.

-- Six user-facing steps replace nine phases on screen; internally VACANCY and DECONSTRUCT
-- become one server-driven ANALYSIS phase.
ALTER TABLE cv_application ADD COLUMN role_title VARCHAR(300);
ALTER TABLE cv_application ADD COLUMN company_name VARCHAR(300);
-- Two or three sentences on what this CV has to show — written once by the analysis.
ALTER TABLE cv_application ADD COLUMN cv_vision TEXT;
-- none | extracted | read | done | empty | failed | legacy. Persisted per stage so an
-- analysis cut off by the stream deadline resumes instead of starting again.
ALTER TABLE cv_application ADD COLUMN analysis_state VARCHAR(16) NOT NULL DEFAULT 'none';
-- The profile step ends only when the user says the profile is right. It used to end the
-- moment the first profile note was approved, which is how a second profile got created.
ALTER TABLE cv_application ADD COLUMN profile_confirmed_at TIMESTAMPTZ;
-- The last step the user was told about, so every step is announced exactly once.
ALTER TABLE cv_application ADD COLUMN announced_step INT;
-- The server-written "Job analysis — …" note, and when it was last rendered (a hand edit
-- after that is never overwritten).
ALTER TABLE cv_application ADD COLUMN analysis_note_id BIGINT;
ALTER TABLE cv_application ADD COLUMN analysis_note_rendered_at TIMESTAMPTZ;
-- The language the user talks in (step messages are written in it), distinct from the
-- advert's language, which the documents are written in.
ALTER TABLE cv_application ADD COLUMN conversation_language VARCHAR(16);
-- Bumped on every transcript save, so a stale device cannot overwrite a newer conversation.
ALTER TABLE cv_application ADD COLUMN transcript_revision BIGINT NOT NULL DEFAULT 0;

UPDATE cv_application SET profile_confirmed_at = now(), phase = 'ANALYSIS'
    WHERE UPPER(phase) IN ('VACANCY', 'DECONSTRUCT');
-- Applications already past the analysis keep their interview as it is.
UPDATE cv_application SET analysis_state = 'legacy'
    WHERE UPPER(phase) NOT IN ('PROFILE', 'ANALYSIS');

-- A requirement row is one QUOTE from the advert; rows sharing `cluster` are one topic, and
-- the topic's analysis is stored on each of its rows.
ALTER TABLE cv_requirement ADD COLUMN topic VARCHAR(120);
ALTER TABLE cv_requirement ADD COLUMN demand TEXT;
-- 1 core · 2 important · 3 bonus
ALTER TABLE cv_requirement ADD COLUMN priority INT;
-- covered | partial | gap
ALTER TABLE cv_requirement ADD COLUMN coverage VARCHAR(16);
-- experience | credential — a credential (a language, a degree) may be backed by the profile
ALTER TABLE cv_requirement ADD COLUMN claim_kind VARCHAR(16);
-- What in the profile pointed at this topic. The profile is a map, not the evidence.
ALTER TABLE cv_requirement ADD COLUMN lead TEXT;
-- JSON array of cv_source_read.source_key the finding rests on.
ALTER TABLE cv_requirement ADD COLUMN sources TEXT;
ALTER TABLE cv_requirement ADD COLUMN finding TEXT;
ALTER TABLE cv_requirement ADD COLUMN draft_line TEXT;
ALTER TABLE cv_requirement ADD COLUMN missing TEXT;
ALTER TABLE cv_requirement ADD COLUMN question TEXT;
-- source (written from her documents) | user (from her answer)
ALTER TABLE cv_requirement ADD COLUMN origin VARCHAR(8);

-- ── Every primary source the analysis read ────────────────────────────────────
-- A topic can only count as covered by a source that was actually read, and the ledger is
-- what that is checked against. Also the corpus the fact and term gates compare with.
CREATE TABLE cv_source_read (
    id             BIGSERIAL    PRIMARY KEY,
    application_id BIGINT       NOT NULL REFERENCES cv_application(id) ON DELETE CASCADE,
    -- resource:123 · repo:owner/name:path/to/file · repo-facts:owner/name
    source_key     VARCHAR(600) NOT NULL,
    label          VARCHAR(300),
    content        TEXT,
    chars          INT          NOT NULL DEFAULT 0,
    ok             BOOLEAN      NOT NULL DEFAULT TRUE,
    -- analysis | chat
    origin         VARCHAR(16)  NOT NULL DEFAULT 'analysis',
    fetched_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_cv_source_read UNIQUE (application_id, source_key)
);

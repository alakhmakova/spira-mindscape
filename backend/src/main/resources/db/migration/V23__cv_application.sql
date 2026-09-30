-- ── The CV & cover letter writer ─────────────────────────────────────────────
-- One row per VACANCY, not per goal: a goal like "find a QA job" holds many
-- applications, each with its own deconstructed requirement list and its own
-- pair of notes. See specs/2026-09-08-cv-and-cover-letter-agent/requirements.md.
--
-- Unlike `ai_grow_session`, this is NOT opaque client JSON. The server reads it
-- on every turn: it is what feeds the model exactly one requirement at a time,
-- which is the whole mechanism that stops the assistant dumping the list and
-- writing a generic CV from the posting's own words.
CREATE TABLE cv_application (
    id               BIGSERIAL   PRIMARY KEY,
    app_user_id      BIGINT      NOT NULL,
    goal_id          BIGINT      NOT NULL REFERENCES goal(id) ON DELETE CASCADE,

    -- "QA-testare — iFacts, Malmö". Shown in the resume list; the user may rename it.
    title            VARCHAR(300) NOT NULL,
    vacancy_url      VARCHAR(2000),
    vacancy_text     TEXT        NOT NULL,

    -- The CV and the letter are written in the VACANCY's language, while the
    -- conversation runs in the user's. Those differ often and the split is a
    -- rule, not an accident, so the language is stored rather than re-guessed.
    vacancy_language VARCHAR(16),

    -- The addressee, when the ad names one. A letter to "To Whom It May Concern"
    -- fares little better than no letter at all (Yate).
    contact_name     VARCHAR(300),

    -- NULL until established during the `vacancy` phase. It decides where the
    -- candidate's motivation goes: into the letter, or — when no letter is
    -- wanted — into SAMMANFATTNING, which then gets a fourth sentence.
    letter_required  BOOLEAN,

    phase            VARCHAR(32) NOT NULL,
    -- How far through the requirement queue the interview has got.
    cursor_index     INT         NOT NULL DEFAULT 0,

    -- Notes this application owns. Bound by PHASE when the user approves the
    -- proposal, never reported back by the model. The profile and story-bank
    -- notes are shared across a goal's applications; the rest belong to this one.
    profile_note_id  BIGINT,
    stories_note_id  BIGINT,
    cv_note_id       BIGINT,
    letter_note_id   BIGINT,
    briefing_note_id BIGINT,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_cv_application_user_goal ON cv_application (app_user_id, goal_id);

-- ── One requirement extracted from the posting ───────────────────────────────
-- A child table rather than a JSON column on the row above (which is what the
-- spec sketched). Three reasons, and the first is the one that matters:
--
--   1. `record_evidence` updates ONE requirement. Against a JSON blob that is a
--      read-modify-write of the whole list on every answer, and two turns racing
--      would silently lose one of them.
--   2. "How many are still pending" and "which cluster comes next" are queries
--      here and hand-written scans there.
--   3. H2 backs the test suite with the schema generated from the entities, and
--      `jsonb` has no clean equivalent there — the trap `book_chunk` already
--      documents. TEXT-holding-JSON would dodge it, but then see (1).
CREATE TABLE cv_requirement (
    id             BIGSERIAL   PRIMARY KEY,
    application_id BIGINT      NOT NULL REFERENCES cv_application(id) ON DELETE CASCADE,

    -- The requirement as the posting states it, verbatim. Kept verbatim ONLY so
    -- the interview can quote it back; it must never reach the CV in these words.
    -- Named `req_text` rather than `text`, which is a type name in both engines and
    -- an unquoted-identifier hazard the moment Hibernate generates the H2 schema.
    req_text       TEXT        NOT NULL,

    -- hard | soft | culture | unclassified. `unclassified` is a real group, not a
    -- fallback: a requirement the model cannot categorise must not be lost because
    -- of that, so it is worked through alongside the must-have hard skills.
    req_group      VARCHAR(16) NOT NULL,
    -- must | nice. Only meaningful for `hard` and `unclassified`.
    weight         VARCHAR(8)  NOT NULL,

    -- Requirements the posting states more than once in different words share a
    -- cluster. Repetition inside one ad is the employer's own emphasis and is the
    -- strongest priority signal available when deconstructing a single posting.
    cluster        VARCHAR(32),
    -- Which paragraph of the ad it came from — shown when a cluster is presented,
    -- so the user can see the same demand appearing in three separate places.
    source_paragraph INT,

    -- Position in the interview queue: clusters first, then must + soft, then nice.
    queue_index    INT         NOT NULL,

    -- pending | answered | no_evidence | skipped
    status         VARCHAR(16) NOT NULL DEFAULT 'pending',

    -- Filled once answered. `gist` is one line of what the story was; the full
    -- story lives in the user-editable story-bank note, never here.
    company        VARCHAR(300),
    gist           TEXT,
    -- The confirmed one-liner this becomes in the CV, played back to the user
    -- before it is used.
    cv_line        TEXT,
    -- Behavioural traits the story demonstrated. Recorded because a story showed
    -- them, never because the posting asked for them.
    traits         VARCHAR(500),
    -- Why nothing could be evidenced, for the disclosure at cv_review.
    no_evidence_reason TEXT,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_cv_requirement_application ON cv_requirement (application_id, queue_index);

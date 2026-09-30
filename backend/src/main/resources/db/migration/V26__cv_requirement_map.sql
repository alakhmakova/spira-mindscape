-- ── The requirement map, and collection by category ─────────────────────────
-- specs/2026-09-16-cv-requirement-map/. The owner wrote the process out by hand (2026-09-16)
-- and it reorders the work: the ADVERT is analysed first, the profile shrinks to a short intake
-- form after it, and evidence is then collected category by category rather than "only the gaps".
--
-- One row of cv_requirement is one thing the employer asks for, in one of five categories:
--   competence  — a short tag for the CV's competence block ("Java", "CI/CD")
--   core        — the one thing the advert is really about; it leads SAMMANFATTNING and the letter
--   requirement — a whole sentence the employer used; becomes a bullet of experience
--   trait       — a personal quality, named or implied, with the advert's line as its context
--   extra       — company/team/level notes: motivation for the letter, or a blocker to raise now

ALTER TABLE cv_requirement ADD COLUMN category VARCHAR(16);
-- yes | no | partial — the user's own verdict, collected as a checklist for competences.
ALTER TABLE cv_requirement ADD COLUMN user_verdict VARCHAR(16);
-- What the user said about it: the checklist comment, or the evidence for a requirement or trait.
ALTER TABLE cv_requirement ADD COLUMN user_answer TEXT;
-- The advert's line that implies a trait, kept so the question carries its context.
ALTER TABLE cv_requirement ADD COLUMN context TEXT;
-- extra only: letter (motivation) | flag (must be raised with the user now)
ALTER TABLE cv_requirement ADD COLUMN extra_use VARCHAR(16);

UPDATE cv_requirement SET category = 'requirement' WHERE category IS NULL;

-- The advert's own facts that belong in the documents.
ALTER TABLE cv_application ADD COLUMN location VARCHAR(300);
ALTER TABLE cv_application ADD COLUMN core_message TEXT;
-- The intake form ("anketa") is the old profile note: a dry skeleton, no experience prose.
ALTER TABLE cv_application ADD COLUMN intake_confirmed_at TIMESTAMPTZ;

-- The new order of the work. PROFILE no longer exists: the advert is read first.
UPDATE cv_application SET phase = 'ANALYSIS' WHERE UPPER(phase) IN ('PROFILE', 'VACANCY', 'DECONSTRUCT');
UPDATE cv_application SET phase = 'REQUIREMENTS' WHERE UPPER(phase) = 'EVIDENCE';
-- An application that had confirmed its profile has its intake confirmed too.
UPDATE cv_application SET intake_confirmed_at = profile_confirmed_at WHERE profile_confirmed_at IS NOT NULL;

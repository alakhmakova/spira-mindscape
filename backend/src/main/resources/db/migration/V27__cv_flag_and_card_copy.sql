-- ── What the live run of the process found ──────────────────────────────────
-- specs/2026-09-16-cv-requirement-map/test-log-2026-09-16.md. The owner walked the whole
-- process by hand against a real advert and kept every defect; these are the two that need
-- a column.

-- Defect 21: `cv_vision` was added by V25 for the design that preceded the requirement map
-- ("two or three sentences on what this CV has to show"). Nothing has ever read or written it.
-- Dropped rather than repurposed: the name suggests it could hold the candidate's motivation
-- and it cannot — it was about the VACANCY — and one column with two meanings is worse than
-- none. IF EXISTS because V25 and V26 may not have run yet on a given database.
ALTER TABLE cv_application DROP COLUMN IF EXISTS cv_vision;

-- Defect 2: a condition of the advert (a mandatory background check, a permit, a stated number
-- of years) was stated in the analysis summary and then abandoned — the same turn moved on to
-- the intake form, so nothing was asked and nothing recorded that it had been raised. The
-- summary now ENDS with the question and step 2 waits, which needs somewhere to record that the
-- analysis has already been reported: announced_step cannot carry it, because the summary and
-- the step-2 announcement are no longer the same event.
ALTER TABLE cv_application ADD COLUMN analysis_reported_at TIMESTAMPTZ;

-- An application already past the analysis has been told about it, whatever was said at the
-- time; without this the summary would be replayed on the next turn of every open session.
UPDATE cv_application SET analysis_reported_at = now()
    WHERE UPPER(phase) NOT IN ('ANALYSIS');

-- ── The CV process runs on the vacancy map ──────────────────────────────────
-- specs/2026-09-17-vacancy-map/, slices 2 and 3. The six-step conveyor (details, then a competence
-- checklist, then one requirement per turn) is replaced by one flexible step: the user fills the
-- vacancy map herself or with the coach, part by part, in whatever order she chooses.
--
-- Four phases collapse into MAP. Stored as the enum's NAME, so a row left on a retired name would
-- fail to load at all — hence the update rather than a parse-time fallback.
UPDATE cv_application SET phase = 'MAP'
    WHERE phase IN ('INTAKE', 'COMPETENCIES', 'CORE', 'REQUIREMENTS', 'TRAITS');

-- The visible steps are renumbered 1 analysis · 2 map · 3 CV · 4 letter. An application that had
-- been told about the old steps 2-4 is set back to 1, so the map step is announced to it afresh —
-- with the map's link and what each part is for, which it has never been shown.
UPDATE cv_application SET announced_step = CASE
        WHEN announced_step IN (2, 3, 4) THEN 1
        WHEN announced_step = 5 THEN 3
        WHEN announced_step = 6 THEN 4
        ELSE announced_step END
    WHERE announced_step IS NOT NULL;

-- The advert's hard conditions (a permit, a background check, a stated number of years), one per
-- line. They used to be `cv_requirement` rows of category `extra` / use `flag`; the map shows them
-- as additional information, but the rule that they are RAISED with the user before anything else
-- needs to know which ones they were.
ALTER TABLE cv_application ADD COLUMN advert_conditions TEXT;

-- `cv_requirement` is NOT dropped here. Applications started before this change hold her answers in
-- it, and they are converted into a map the first time each one is opened (CvMapService.ensure).
-- Dropping the table is a later migration, once those have had the chance to convert.

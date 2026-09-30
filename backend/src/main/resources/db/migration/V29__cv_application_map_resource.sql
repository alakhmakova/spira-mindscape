-- ── The application's vacancy map ───────────────────────────────────────────
-- specs/2026-09-17-vacancy-map/. The requirement map moves out of `cv_requirement` rows and the
-- HTML note that rendered them, and into a `vacancy` resource the user edits directly.
--
-- Its OWN column rather than reusing `analysis_note_id`: while the map and the old note both
-- exist (slice 1 of that spec), one column cannot point at two different resources. When the note
-- goes, `analysis_note_id` and `analysis_note_rendered_at` go with it — the timestamp exists only
-- to stop a re-render clobbering a hand edit, which field-level patches make structural.

ALTER TABLE cv_application ADD COLUMN map_resource_id BIGINT;

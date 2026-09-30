-- ── The vacancy map ─────────────────────────────────────────────────────────
-- A fifth resource type. One page per vacancy, holding the advert's own facts and the user's
-- answers to them as a structured document rather than as prose — see
-- specs/2026-09-17-vacancy-map/. It replaces the note the CV writer used to render the
-- requirement map into: a note is one blob, so filling in one answer meant rewriting the whole
-- thing, and every hand edit the user had made was lost with it.
--
-- The document lives in ONE JSON column rather than in tables of its own. What it holds is a
-- tree the page owns end to end (the vacancy's facts, a skills checklist whose items carry
-- comment threads, a cloud of quality pills, a requirement x company matrix, the company block)
-- and nothing queries across it. A row per field would buy joins nobody runs, and a migration
-- every time the design grows a card.
--
-- It is deliberately NOT selected by the list projections (ResourceView): like data_url it is
-- loaded on demand when the page opens, so a goals fetch never drags every map across the wire.
-- That is the egress rule ResourceView exists to enforce.

-- Postgres names a column CHECK `<table>_<column>_check`. IF EXISTS because a database restored
-- from a dump may carry a differently-named constraint; the ADD below is what actually matters.
ALTER TABLE resource DROP CONSTRAINT IF EXISTS resource_type_check;
ALTER TABLE resource ADD CONSTRAINT resource_type_check
    CHECK (type IN ('note', 'link', 'file', 'email', 'vacancy'));

ALTER TABLE resource ADD COLUMN map_data TEXT;

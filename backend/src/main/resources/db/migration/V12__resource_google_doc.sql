-- Link a note resource to the Google Doc created from it, so "Open in Google Docs"
-- reopens the SAME document instead of creating duplicates, and the note can push
-- updates to it. Nullable: only set once a note has been exported at least once.
ALTER TABLE resource
    ADD COLUMN drive_file_id       TEXT,
    ADD COLUMN drive_web_view_link TEXT;

# GROW coaching library

Put the coaching books here as **UTF-8 plain text**, one file per book,
paragraphs separated by a blank line:

- `coaching-for-performance.txt`
- `coach-the-person.txt`

The file name becomes the book title shown to the AI
(`coaching-for-performance.txt` → "Coaching for Performance").

On backend startup, `BookIngestionRunner` chunks every `*.txt` in this folder
into the `book_chunk` table (idempotent — already-ingested books are skipped).
Embeddings are computed lazily during the first GROW session using the user's
Mistral API key.

To re-ingest a book after changing its text:
`DELETE FROM book_chunk WHERE book = '<Title>';` and restart the backend.
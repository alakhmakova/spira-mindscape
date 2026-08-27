# GROW coaching library — retired, and deliberately empty

**Do not put book texts in this folder.** It is empty on purpose and `*.txt` here is
gitignored.

## Why

Until 2026-08-22 the AI coach had no written method of its own: every turn, six passages
were retrieved from two coaching books by embedding similarity and the model was told they
were its only method. That was replaced by
[`../prompts/grow/coach-method.md`](../prompts/grow/coach-method.md) — the method written
out as prose, hand-distilled, loaded by `ai/prompt/PromptResources.java`. See the banner at
the top of [`docs/grow-sessions-rag-guide.md`](../../../../../docs/grow-sessions-rag-guide.md)
for what went wrong with retrieval and why.

Nothing on the session path retrieves anything now, so the books earned nothing and cost
something: they are **copyrighted commercial works**, and holding their full text in a
public repository — and shipping it inside the JAR — is reproduction of the whole work.
They were removed on 2026-08-23 and `spira.books.enabled` now defaults to `false`.

## What is still here

`BookIngestionRunner`, `BookChunker`, `MistralEmbeddingClient`, `GrowLibraryService` and
the `book_chunk` table all remain, with their tests, in case a **curated** library is ever
wanted — material written or licensed for this purpose, not someone else's book. They have
no production caller.

To switch such a library back on: add UTF-8 `.txt` files here (paragraphs separated by a
blank line; the file name becomes the title the AI sees), set `spira.books.enabled=true`,
and give the chat path a reason to call `GrowLibraryService` again. Re-ingesting a changed
text needs `DELETE FROM book_chunk WHERE book = '<Title>';` and a restart.

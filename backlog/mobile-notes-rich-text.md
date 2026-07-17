# Mobile notes are plain text — restore rich formatting later

- **ID:** BUG-007 (enhancement — tracked at the user's request)
- **Status:** 🐞 Open
- **Reported by:** User (decision during the mobile design/parity spec)
- **Area:** Android app — Resources → note editing
- **Severity:** Low (feature parity gap, not a defect)

## Summary

On the web, a note resource's body is edited with a **rich-text editor** (TipTap/ProseMirror,
`src/components/spira/RichTextEditor.tsx`) — headings, bold/italic, lists, links, colors — and the
body is stored as an **HTML string**. For the first native-mobile version we deliberately use a
**plain-text** field for note bodies (a full rich editor on Android is heavy). Plain text
round-trips fine through the same `body` string field; it just loses formatting.

## What to do later

Add a rich-text (or at least Markdown) editing experience for notes on Android so mobile matches
the web:
- Either a lightweight Markdown editor that renders to the stored HTML, or a Compose rich-text
  editor, kept consistent with how the web stores `body` (HTML).
- Preserve existing notes: web bodies are HTML; a plain-text mobile view currently shows flattened
  text (`stripHtml`-style). The richer editor should read/write the same HTML the web uses.

## How to verify fixed

- Create/edit a note on mobile with formatting; open it on web — formatting is preserved, and
  vice-versa.

## Resolution

_(empty — open)_

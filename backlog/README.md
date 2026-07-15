# Backlog — bug tracker

One file per bug. This is where **known bugs** live until they are fixed. It is mostly fed by
the **user** proposing bugs; findings from `/code-review` do **not** belong here (those are
fixed in the same change, not tracked as standing bugs).

## Rules

- **All documentation in English.**
- **One bug = one Markdown file** with a clear, descriptive name (e.g.
  `cross-device-data-not-refreshing.md`), not `bug1.md`.
- Every bug file must make it **unambiguous whether the bug is fixed or not** via a `Status`
  field at the top: `🐞 Open`, `🔧 In progress`, or `✅ Fixed`.
- Each bug documents: summary, **steps to reproduce**, **root cause** (once understood), the
  **fix approach**, **how to verify fixed**, and a **Resolution** section filled in when done.
- When a bug is fixed, flip `Status` to `✅ Fixed` and complete the Resolution section
  (files changed; the user commits manually, so reference the change, not a Claude commit).

Claude should read this folder when starting work and **remind the user about open bugs** so
the accumulated backlog gets addressed.

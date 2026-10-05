# A provider rate-limit is shown twice on screen, in two different notice shapes

- **ID:** BUG-068
- **Status:** ✅ Fixed
- **Reported by:** Claude, running a live 15-minute GROW session at the owner's request (BYOK
  Mistral, `mistral-large-latest`), 2026-09-02
- **Area:** Frontend AI chat (`src/components/ai/AiPanel.tsx`, `src/components/spira/Notice.tsx`)
- **Severity:** Medium — cosmetic/consistency, but it directly re-creates the exact failure mode
  the notice-design spec in `CLAUDE.md` says was eliminated

## Summary

`mistral-large-latest` on a BYOK key hits its rate limit often (expected on a low/free tier). When
it does, **two** separate "Rate limit exceeded" cards render on screen **simultaneously**, in two
different visual treatments:

- an **amber/warning**-styled card (`TriangleExclamationFill`, `#C99500` border, no dismiss button)
- a **red/error**-styled card (`CircleExclamationFilled`, `#C53336` border, with a dismiss `×`)

Both say the identical text, both are visible at once, and the amber one does not self-clear even
though it is not the "error" kind (non-error notices are supposed to clear after
`PANEL_NOTICE_MS` = 6000ms per `AiPanel.tsx:107,1361-1365`).

This is the same user-facing problem `CLAUDE.md`'s "Notices and toasts" section describes as
already fixed: *"An error is not a message… it used to be written into the transcript as a turn
**and** raised as a notice, so a provider's quota error appeared twice on one screen in two
shapes."* The mechanism is different this time (two notice cards instead of a transcript turn plus
a notice), but the user sees the same thing: one failure, told twice, two shapes.

## Steps to reproduce

1. Connect a Mistral key with a low rate limit (BYOK, Settings → AI providers → Mistral → Use
   this).
2. Start a GROW session on any goal and send messages quickly enough to hit the provider's rate
   limit (happened on roughly every other turn in testing).
3. Observe the footer of the AI panel: an amber "Rate limit exceeded" card and a red "Rate limit
   exceeded" card both present at once. Wait 10+ seconds — the amber one is still there.

## Root cause

Not fully isolated. What is confirmed by reading the code:

- The AI panel's own toast/notice type, `PanelNotice` (`AiPanel.tsx:96`), only has two kinds:
  `"success" | "error"`. Its one render site (`AiPanel.tsx:3164-3174`) passes `notice.kind`
  straight through, so **this path can never produce the amber card** — the amber card must come
  from elsewhere.
- `Notice.tsx:29,48-53` confirms the amber styling (`ink: "#C99500"`, `TriangleExclamationFill`) is
  the app's real `warning` kind, not a browser/extension artifact — and the file's own comment says
  warning is *"the same `#C99500` the assistant's error turn uses"*, suggesting a second, separate
  code path renders a failed turn with `kind="warning"` in addition to the panel's own
  `kind="error"` toast. That second call site was not located in the time available — worth a
  targeted `grep` for `NoticeCard` / `kind="warning"` outside `AiPanel.tsx` and in the transcript's
  per-turn rendering.

## Fix approach (proposed)

- Find the second emitter (a per-turn/streaming-failure indicator, likely rendered inline near the
  transcript rather than in the shared footer notice) and either suppress it when the same failure
  is about to raise the terminal `chatToast.error`, or vice versa — one failure, one card, per the
  existing spec.
- If the amber one is meant to be a distinct "retrying…" signal shown *before* the terminal error,
  it must not persist once the terminal error lands, and it must still obey `PANEL_NOTICE_MS` or its
  own equivalent timeout.

## How to verify fixed

- Reproduce steps above; only one "Rate limit exceeded" card should ever be visible at a time, and
  it should clear on its own unless it is the terminal `error` kind (which keeps its `×`).

## Resolution

Fixed 2026-09-02. Root cause found: `sendGrow`'s `onError` handler in `AiPanel.tsx` both filled the
failed turn's placeholder in with the error (`error: true`, rendered in the amber/warning styling
`CLAUDE.md` reserves for a failed transcript turn) **and** called `chatToast.error(msg)` (the red
footer notice) — the exact anti-pattern the notice spec's own prose already names as fixed. The
regular (non-GROW) chat's `onError`, a few hundred lines above it in the same file, already does
this correctly — `setMsgs((p) => p.filter((m) => m.id !== id)); chatToast.error(msg);` — and says
so in its own comment. `sendGrow`'s handler had drifted from that pattern; brought in line with it:
the failed turn's placeholder is now removed rather than filled in, so only the one red notice
shows.

Verified live: reproduced the real Mistral rate limit in a running GROW session before and after
the fix. Before: an amber transcript bubble plus a separate red "Rate limit exceeded" toast, both
on screen at once. After: only the red toast, with dismiss `×`; the transcript shows nothing for
the failed turn.

Files changed: `src/components/ai/AiPanel.tsx`. Verified: `tsc --noEmit` clean, `npm run lint`
clean, `npm test` 267/267 passing. Checked Android for the same pattern — it does not exist there
(the chat-stream error only ever fills the inline `isError` bubble; there is no separate
toast/notice tied to that path), so no Android change was needed.

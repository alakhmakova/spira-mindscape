# An email resource's proposal card says "contact", carries the wrong icon, and hides its own content

- **ID:** BUG-078
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-09-03 — asked the plain chat to create an email resource and got
  a card reading "NEW CONTACT" with only the name visible, no way to see the address; also
  reported the card's bottom edge cut off on desktop and (differently) on mobile web, and asked
  for every resource kind to be checked for the same class of problem.
- **Area:** AI proposal cards, web + Android (`src/components/ai/proposal-logic.ts`,
  `src/components/ai/AiPanel.tsx`, `android/.../data/ai/Proposal.kt`,
  `android/.../ui/ai/ProposalCard.kt`) and, separately, the coach panel's own layout
  (`src/styles.css`)
- **Severity:** Medium — the wording/icon issues are cosmetic-but-wrong; the missing content is a
  real usability defect (the user cannot see what they're about to accept); the desktop clipping
  is a genuine layout bug

## Summary

Four separate defects, found by checking every resource kind after the report, not just email:

1. **Wording**: the email create/edit card badge read "New contact" / "Edit contact". CLAUDE.md's
   own spec (Components and chrome → 7) already states plainly: *"The word is Emails, never
   'Contacts'."* Also `chatToast.success("Contact added"/"Contact updated")`, and one About-page
   sentence ("Notes, links, files and contacts…").
2. **Icon**: the email kind used the generic sparkles glyph — the same mark six other kinds use —
   instead of an envelope. A proper `mail` (Gravity `envelope`) icon didn't exist yet in
   `AiPanel.tsx`'s `PATHS` table; Android's `SpiraIcons.Mail` already existed but wasn't wired up
   for `EMAIL`.
3. **Missing content, both platforms** — the actual bug behind "вижу только название ресурса но
   нет возможности увидеть контент": `proposal-logic.ts` (and its Kotlin mirror) set `detail` to a
   **generic label** — `"New contact"`, `"New link"`, `"Edit contact"`, `"Edit link"` — literally
   the same string as the kind badge above it. `ProposalBody`'s (and Android's matching) own
   redundancy check hides `detail` whenever it exactly matches the badge label, so on a card whose
   headline is the resource's **name**, the actual email address / role / phone, or the actual
   URL, never appeared anywhere on the card, at any step, before accepting. Note was already fine
   (`body` + "Read full content"); this was specific to `link`/`email`/`edit_link`/`edit_email`,
   which carry their real content in `patch`, never in `detail`.
4. **Desktop clipping**: `.ai-panel-left-resize-handle` in `styles.css` was still positioned via
   `right: -4px` — left over from when the coach panel docked on the LEFT of the page, before the
   2026-08-23 move to the right. Since the panel now sits flush against the viewport's right edge,
   the handle stuck 4px past it, a real (if small) horizontal page overflow. On Windows/Chrome that
   is enough to draw the native horizontal scrollbar, which renders as an opaque bar along the very
   bottom of the viewport — directly through the floating footer card's Accept/Edit/Dismiss row,
   cutting it off in a straight line. Confirmed by measuring the DOM (`getBoundingClientRect`)
   before and after: `right: 1909` against a `1905`px viewport before the fix, `0` overflowing
   elements after.

## What did NOT turn out to be a bug

- The reported "mobile web — cut off crookedly" was checked on a real narrow viewport (Chrome on
  an Android emulator, genuine `useIsMobile()` bottom-sheet layout, not desktop DevTools
  emulation): the same email proposal card rendered cleanly, full Accept/Edit/Dismiss row, normal
  margin below it — no clipping of any kind. The resize handle responsible for the desktop bug
  isn't even present in that code path (`hidden md:flex` — the handle only renders in the
  desktop `<aside>`, never in the mobile `<Drawer>`). The most likely explanation is that the
  photo the report was based on simply didn't fully capture the screen (camera angle/framing) —
  it's a real photograph of a device, not a screenshot. If it recurs, a proper (non-rotated)
  screenshot from the actual phone would pin it down; nothing in the code points at a second,
  mobile-only cause.

## Fix approach

- **Wording** (both platforms): `"New contact"` → `"New email"`, `"Edit contact"` → `"Edit
  email"`, `"Contact added"/"Contact updated"` → `"Email added"/"Email updated"`, and the About
  page's "contacts" → "emails". Android's nameless-email fallback ("Contact") now falls back to
  the email address itself before "Email", matching the web's `resourceDisplayName`.
- **Icon**: added `PATHS.mail` (Gravity `envelope`, the same path as `src/components/spira/
  icons.tsx`'s `Mail`) to `AiPanel.tsx` and used it for `email`'s `KIND_META`; wired
  `SpiraIcons.Mail` into Android's `kindIcon` for `ProposalKind.EMAIL`.
- **Content**: `proposal-logic.ts` and `Proposal.kt` now set `detail` to the real payload —
  `[email, role, phone].join(" · ")` for `email`/`edit_email`, and the URL for `link`/`edit_link`
  when a label was given (so the URL doesn't otherwise vanish behind the label headline) — falling
  back to the old generic label only when there's nothing else to show (which the redundancy check
  then correctly hides, same as before).
- **Desktop clipping**: `.ai-panel-left-resize-handle` now uses `left: -4px` (matching the base
  `.resize-handle`, correct for a right-docked panel), instead of `right: -4px`.

## How to verify fixed

- Ask the coach (plain chat) to create an email resource with a name, address, role and phone.
  The card should read "NEW EMAIL" with an envelope mark, and the address/role/phone should be
  visible under the name before accepting.
- On a desktop browser window, confirm no horizontal scrollbar appears while the coach panel is
  open, and that a pending card's Accept/Edit/Dismiss row always has clear space below it.

## Resolution

Fixed 2026-09-03. Files changed: `src/components/ai/proposal-logic.ts`,
`src/components/ai/AiPanel.tsx`, `src/lib/spira/about-content.ts`, `src/styles.css`,
`android/app/src/main/java/com/spiramindscape/android/data/ai/Proposal.kt`,
`android/app/src/main/java/com/spiramindscape/android/ui/ai/ProposalCard.kt`,
`android/app/src/main/java/com/spiramindscape/android/ui/ai/AiChatScreen.kt`. Tests added:
`src/components/ai/proposal-logic.test.ts` (4 new cases) and
`android/app/src/test/java/com/spiramindscape/android/data/ai/ProposalTest.kt` (4 new cases).

Verified: web `tsc --noEmit` clean, `npm run lint` clean (0 new warnings), `npm test` 271/271
passing; Android `:app:compileDebugKotlin` succeeds, `ProposalTest` passing. The desktop clipping
fix was verified live in a real browser by measuring the DOM before/after (no more elements
overflowing the viewport, screenshot confirmed the card's full rounded bottom edge with margin
below it). The content-visibility and wording/icon fixes were verified live against the real
(pre-existing, cross-device-synced) proposal card, which picked up the new "NEW EMAIL" badge and
icon immediately; a stale `detail` string on that specific already-persisted proposal is expected
(proposals are parsed once, not recomputed retroactively) and is not evidence against the fix — a
freshly created email resource was independently checked against real narrow-viewport mobile
Chrome (Android emulator) and showed the clean, unclipped card described above.

The user commits manually — nothing has been committed by Claude.

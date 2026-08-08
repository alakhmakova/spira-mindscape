# Requirements: the AI assistant on Android

The assistant the web has had since `specs/2026-06-07-ai-assistant-cards-and-drawers/`, brought to
the native app — talking to the same `/api/ai` endpoints, over the same session cookie, against the
same encrypted keys.

Status: **built 2026-08-06.** Primary code: `data/ai/` (API, proposal and transcript models) and
`ui/ai/` (panel, cards, provider sheet, swipe host).

---

## 0. Look

The panel wears the **desktop design**, on the brand's **Kale-600** ground (`#005961`) instead of
the web's `#006d67`:

- the **wordmark** across the top — "spira" 27sp bold beside "ai coach" in white/74 — with
  "New chat" (only once there is something to clear) and Close on the right;
- a **provider strip** under it: "Bring your own key" on the left, and on the right a status dot
  with the live provider set in mono. The dot uses **Kale-300** for ready and **Guava-300** for
  "needs a key" — the brand's equivalents of the web's ad-hoc mint/amber, which hold up on teal;
- the conversation **on the ground itself**: the user's turns in white bubbles with the
  bottom-right corner cut, the assistant's prose set straight on the teal with no bubble at all,
  and Copy under both;
- one **white composer card** at the foot, holding the field with the paperclip, "Start GROW
  session" and Send along its bottom edge.

Dark type on the white bubbles and composer is **Kale-600**, keeping the web's dark-teal-on-white
relationship while staying inside the palette.

## 1. How it is reached — swipe up, not a screen

The assistant is a **pane over the content**, not a destination in the navigation graph:

- **Open** by swiping **up on the goal-workspace footer**, or by tapping the assistant's sparkle
  (the footer's middle action, and the All-goals header's AI icon).
- **Close** from the chevron in the pane's own header, with the **back** gesture, or by dragging
  the pane's top handle back down.
- A drag past **a third** of the height, or a flick faster than 600 px/s, settles the way it was
  heading; anything less springs back, so a half-swipe never strands the user in between.

**Why up, and not sideways.** The pane originally slid in from the right screen edge, which put it
on the **same axis** as the workspace's horizontal tab swiping — the two gestures competed, and the
opening drag had to be confined to a narrow 24dp edge strip to keep them apart. Now the axes are
split: horizontal switches GROW phases, vertical brings the assistant up. That frees the whole
footer to be the grab area, which is far easier to find than a hidden strip, and it removes the
need for any swipe *between* the chat and the page.

## 2. Scope

`goalId` scopes the conversation. A goal id gives the assistant that goal's reality, options,
targets and resources; null is the all-goals chat, which is the only place a **new goal** can be
proposed. Each scope keeps its **own transcript**, keyed in the view-model store, so switching
goals never mixes two conversations.

## 3. What it does

| Capability | Where |
|---|---|
| Streaming chat (SSE: `token` / `proposal` / `status` / `done` / `error`) | `AiApi.streamChat` |
| Markdown replies (headings, lists, quotes, fenced code, inline emphasis) | `AiMarkdown` |
| Proposal cards: Accept / Edit / Dismiss, per-field aspect ticks | `ProposalCard` |
| Applying an accepted proposal through the ordinary goal actions | `ProposalApply` |
| GROW sessions: length picker, live timer, closing turn, end card, save memory | `AiChatViewModel` |
| Provider + API key + model management | `ProviderSheet` |
| File attachments (images downscaled, PDF, DOCX) | `ChatAttachments` |
| Cross-device transcript sync | `AiApi.getTranscript` / `putTranscript` |

## 4. The transcript is a contract

The transcript syncs through `/api/ai/chat/transcript` as a JSON array, so a conversation started
on the phone opens on the laptop. That makes the field names in `ChatMessage` / `Proposal`
**wire format**, matched field-for-field to the web `Msg` and `Proposal` types — not an
implementation detail. `TranscriptTest` locks the round-trip, including a transcript written by the
web parsing here.

Rules carried over from the web:

- The in-flight placeholder is **never stored** and never sent as history.
- Attachment **bytes are stripped** from what is stored; a transcript adopted from the server keeps
  whatever bytes this device still holds (`mergeAttachmentBytes`).
- Only the last **100** messages are kept.
- Consecutive same-role turns are **merged** before being sent as history — a card revise writes the
  user's instruction into the transcript, so two user turns in a row are ordinary.

## 5. Proposals

The assistant never changes anything on its own. `propose_goal_change` arrives as a tool call, is
parsed by `proposalFromToolArgs` (a port of the web `proposal-logic.ts`), and becomes a card.

- **Accept** applies the change through the same `GoalWorkspaceActions` the user's own taps use, so
  an AI edit and a hand edit are indistinguishable downstream — optimistic update, server write,
  refetch on failure.
- **Edit** sends the user's instruction *with the whole proposal attached* (`proposalContext`), so a
  second change can't lose the first.
- **Aspects** — a create proposal's optional extras (a deadline, a confidence, a description) each
  get their own tick, so the user can keep the goal but skip the date.
- A proposal that belongs elsewhere (creating a goal from inside one goal) says so rather than
  silently doing nothing.
- The decision is recorded server-side when the proposal was persisted, so it survives a reload and
  follows the user to their other devices.

## 6. Keys

The key is typed once and sent straight to the server, which encrypts it. The app never keeps a
copy; the server only ever hands back a hint (the last few characters). Choosing a provider saves
the choice through `/api/ai/preferences`, so it follows the user across devices. Until a key exists
for the chosen provider the status dot goes Guava, the composer is disabled, and the opening screen
leads with "Add an API key to start chatting" — a 422 from the chat endpoint flips the same state.

## 7. Not ported

- **Panel resizing** — a desktop affordance; the pane is full-width on a phone.
- **Stepped create cards** (the multi-screen create flow the web shows for some proposals). Android
  shows the single card with aspect ticks, which is the same decision in one screen.
- **The handoff stash** (`open_goal` re-running a request after navigating). Android names the goal
  and leaves the navigation to the user.

## Tests

`ProposalTest` (parsing, aspects, dedup, revise context, history merging), `TranscriptTest` (the
cross-device round-trip), `AiMarkdownTest` (the block and inline grammar). `VisualCheckAiChatTest`
and `VisualCheckAiEmptyTest` render the panel — a reply with a card, and the opening prompts built
from a goal's actual state — to `app/build/reports/visual/`.

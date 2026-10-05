# Chat history is unbounded, so an old chat costs more every turn

- **ID:** BUG-056
- **Status:** ✅ Fixed
- **Reported by:** Found while investigating BUG-055 (slow chats on production), 2026-08-27
- **Area:** Backend (`ai/chat/AiChatService`, `ai/chat/dto/ChatRequest`), Web
  (`components/ai/proposal-logic.ts`), Android (`data/ai/Proposal.kt`)
- **Severity:** **Medium** — not the cause of the three-minute stalls, but it is why a
  long-running chat feels slower than a fresh one, and it is an unbounded client-controlled input
  on a metered API

## Summary

Nothing limited how much conversation was replayed to the model on each turn. Both clients sent
their whole stored transcript and the server copied all of it into the prompt:

- web `buildHistory` returned every non-error, non-streaming turn from a store holding **100**
  messages;
- Android `buildHistory` did the same;
- `ChatRequest.history` had no `@Size`, its entries had no length cap at all, and
  `AiChatService.buildMessages` looped over the list verbatim.

So a conversation cost more with every turn it survived: bigger request, bigger prompt, longer time
to the first token, more tokens billed. Cloud Run's request log for one afternoon (27 Aug 2026)
shows the shape plainly — the same chat re-posting **270 KB, then 276 KB, then 276 KB**, and then
3.7 KB the moment the user pressed "new chat".

It also meant a signed-in user could post an arbitrarily large body to a paid endpoint.

## Steps to reproduce

1. Open a goal's assistant and hold a long conversation — thirty or forty turns, or paste a couple
   of long documents into it.
2. Watch the request size of `POST /api/ai/chat` in the network panel, and the time to the first
   token.
3. **Before the fix:** the body grows monotonically and never resets; replies get slower. Pressing
   "New chat" makes it fast again, which is the tell.

## Root cause

The transcript was treated as context to be preserved rather than as a budget to be spent. Neither
client trimmed, and the server had no backstop, so the only thing that ever shortened a
conversation was the user starting a new one.

## Fix approach

The same two limits in all three places — **60 turns** and **30,000 characters**, newest first:

- server: `ai/chat/ChatHistory.trim`, applied in `buildMessages`; plus `@Size(max = 500)` on
  `ChatRequest.history` as an abuse stop, far above any honest conversation, so a request cannot
  arrive with a million entries and be parsed into the heap before anything looks at it;
- web: `trimHistory` inside `buildHistory` (`HISTORY_MAX_ENTRIES` / `HISTORY_MAX_CHARS`);
- Android: `trimHistory` inside `buildHistory` (same two names).

Two limits rather than one because they catch different things: the count bounds the per-message
overhead the provider validates and bills, the characters bound what actually costs tokens — a
single pasted document blows the budget in one entry while the count still says two.

Three rules that are easy to get wrong later, and are therefore each pinned by a test on all three
surfaces:

- **Trim after merging.** Two same-role messages in a row are one turn to the model; trimming first
  would count them as two and could cut between them, leaving half an instruction.
- **The newest turn always survives**, truncated to its **tail** if it alone exceeds the budget —
  the question is at the end of a long paste, and dropping the turn would have the model answer
  something it was never shown.
- **Never start on an assistant turn.** Anthropic rejects a conversation whose first message is not
  the user's, so a trim landing on a reply would turn a long chat into a 400. A window holding only
  assistant turns drops to nothing.

The clients trim so the request stays small on the wire (which on mobile data is the cost that
matters); the server trims because the history is client-supplied and an old build, another client
or a script is not bound by what the current web bundle does.

## How to verify fixed

- `ChatHistoryTest` (11 cases) — both limits, the tail truncation, the role rule, order
  preservation, null entries.
- `proposal-logic.test.ts` — the same six behaviours on the web.
- `ProposalTest` — the same six on Android.
- By hand: hold a long chat and watch the request size level off instead of climbing.

## Resolution

Fixed 2026-08-27, alongside BUG-055 (found in the same investigation).

The thing worth keeping: **the numbers live in three files and have to agree.** That duplication is
deliberate — the server has to hold the line for clients it does not control, and the clients have
to hold it so the bytes are never sent — but it means changing one is changing three. Each of the
three says so, and names the other two.


## Corrections from the code review (2026-08-28)

- **The cap did not cap what it claimed to.** `@Size(max = 500)` bounded the entry *count* and
  nothing bounded an entry's *length*, and without `@Valid` a per-entry constraint would not have
  been evaluated anyway — so the javadoc's claim that a request "cannot be parsed into the heap
  before anything looks at it" was false. There is no `server.tomcat.max-http-*` setting anywhere
  in the app either. Now `@Valid` on the list, `@Size(max = 200)` on the count, and
  `@Size(max = 100_000)` on each entry's content.
- **The trim could discard the entire history**, and not in a rare case. The newest entry is
  normally the assistant's last reply; if the user turn before it did not fit in what was left of
  the budget, the window held one assistant entry, and stripping the stranded assistant returned
  **nothing** — the model silently lost all context. A single long reply near the 30k budget was
  enough. The fall-back is now the newest **user** turn on its own, truncated: it can lead, and one
  real thing the user said beats no history at all. Tested on all three surfaces.

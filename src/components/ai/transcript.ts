import type { ChatAttachment } from "./ai-api";
import type { Proposal } from "./proposal-logic";

/** One line of a conversation, as the panel holds it and as the transcript stores it. */
export type Msg = {
  id: string;
  role: "user" | "assistant" | "system" | "end" | "closed";
  content: string;
  streaming?: boolean;
  proposals?: Proposal[];
  error?: boolean; // an error bubble — rendered with a warning icon, excluded from history
  /** Ephemeral progress line (GROW library indexing). Display-only: never part
   *  of content, so it can't leak into the transcript or the model history. */
  status?: string;
  /** Files attached to this (user) message — shown as chips; not persisted. */
  attachments?: ChatAttachment[];
  /** Set on a user message that came from a card's "Edit" box: the headline of the card
   *  being revised, shown as a caption above the bubble so the request is traceable. */
  revisedLabel?: string;
};

/**
 * Parses a stored transcript JSON string into messages, or null if unusable.
 *
 * **Session notes are dropped on the way in.** "Session ended…" is a transient line — it is
 * posted when a session closes and removed a few seconds later by `postSessionNote`. That timer
 * lives in the page, so closing the tab (or the app) inside those seconds leaves the note behind
 * in a transcript that has already been written to the server, where it then sits for good: the
 * exact thing BUG-077 was raised for, and what the owner's 5 September session still showed. A
 * timer alone cannot make something ephemeral; dropping it whenever the transcript is read can,
 * and it heals the notes already stranded out there on their next load.
 *
 * The Android twin is `parseTranscript` in `data/ai/ChatMessage.kt`, and it drops the same line —
 * the two parse one shared blob, so a rule only one of them follows is not a rule.
 */
export function parseTranscript(
  content: string | null | undefined,
): Msg[] | null {
  if (!content) return null;
  try {
    const parsed = JSON.parse(content) as Msg[];
    return Array.isArray(parsed)
      ? parsed.filter((m) => m.role !== "system")
      : null;
  } catch {
    return null;
  }
}

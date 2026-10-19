import { create } from "zustand";

/**
 * Which Upcoming event's card is open — the card that asks **Done, Won't do or Postpone**
 * (`EventDecision.tsx`, owner 2026-10-19). Three doors lead to it: Open on a reminder that has
 * gone off on the event's own day (the "decide now" page and the notification's own button), and
 * "Let's do it" on the Important events timeline. It is app-wide state so the card outlives the
 * page that opened it: answering "decide now" closes that page, and the card must still be there.
 */
type EventDecisionState = {
  eventId: string | null;
  open: (eventId: string) => void;
  close: () => void;
};

export const useEventDecision = create<EventDecisionState>((set) => ({
  eventId: null,
  open: (eventId) => set({ eventId }),
  close: () => set({ eventId: null }),
}));

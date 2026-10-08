import { create } from "zustand";

type AiCtx = { goalId?: string };

type State = {
  isOpen: boolean;
  /**
   * **Open, but parked** — the coach and the standing navigation share the left edge, so only
   * one of them is on screen at a time (owner, 2026-10-07: "либо чат, либо меню"). Opening the
   * nav over an open chat sets this rather than closing it, because closing would unmount the
   * panel and take the scroll position, the half-typed message and the pending proposal card
   * with it ("информация из чата не должна быть потеряна"). The panel stays mounted and is
   * hidden; the nav's coach mark pulses to say where it went.
   */
  collapsed: boolean;
  isWide: boolean;
  context: AiCtx;
  mode: "assistant" | "coaching";
  /**
   * The key sheet, asked for from outside the panel — the nav's key button (owner, 2026-10-07).
   * It is the panel's own surface and stays so; this is only the request to show it, which
   * `AiPanel` consumes and clears, so the flag cannot get stuck on.
   */
  keysWanted: boolean;
  open: (ctx?: AiCtx) => void;
  /** Park an open panel. A no-op when the panel is closed — there is nothing to park. */
  collapse: () => void;
  openKeys: () => void;
  keysShown: () => void;
  close: () => void;
  setWide: (wide: boolean) => void;
  setMode: (m: "assistant" | "coaching") => void;
  setContext: (c: AiCtx) => void;
};

export const useAi = create<State>((set) => ({
  isOpen: false,
  collapsed: false,
  isWide: false,
  keysWanted: false,
  context: {},
  mode: "assistant",
  open: (ctx) =>
    set((s) => ({ isOpen: true, collapsed: false, context: ctx ?? s.context })),
  collapse: () => set((s) => (s.isOpen ? { collapsed: true } : {})),
  openKeys: () => set({ isOpen: true, collapsed: false, keysWanted: true }),
  keysShown: () => set({ keysWanted: false }),
  close: () => set({ isOpen: false, collapsed: false, isWide: false }),
  setWide: (isWide) => set({ isWide }),
  setMode: (mode) => set({ mode }),
  setContext: (context) => set({ context }),
}));

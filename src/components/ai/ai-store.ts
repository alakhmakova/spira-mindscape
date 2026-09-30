import { create } from "zustand";

type AiCtx = { goalId?: string };

type State = {
  isOpen: boolean;
  /**
   * Desktop only: the coach is open but folded to a strip of icons, because the side navigation
   * was expanded over it (owner, 2026-09-17 — clicking the collapsed menu opens it, and the chat
   * shrinks to a strip rather than being covered by an overlay). The panel stays MOUNTED, so a
   * conversation in progress is only out of sight, never lost.
   */
  minimized: boolean;
  isWide: boolean;
  context: AiCtx;
  mode: "assistant" | "coaching";
  open: (ctx?: AiCtx) => void;
  close: () => void;
  setMinimized: (minimized: boolean) => void;
  setWide: (wide: boolean) => void;
  setMode: (m: "assistant" | "coaching") => void;
  setContext: (c: AiCtx) => void;
};

export const useAi = create<State>((set) => ({
  isOpen: false,
  minimized: false,
  isWide: false,
  context: {},
  mode: "assistant",
  open: (ctx) =>
    set((s) => ({ isOpen: true, minimized: false, context: ctx ?? s.context })),
  close: () => set({ isOpen: false, minimized: false, isWide: false }),
  setMinimized: (minimized) => set({ minimized }),
  setWide: (isWide) => set({ isWide }),
  setMode: (mode) => set({ mode }),
  setContext: (context) => set({ context }),
}));

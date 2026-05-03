import { create } from "zustand";

type AiCtx = { goalId?: string };

type State = {
  isOpen: boolean;
  isWide: boolean;
  context: AiCtx;
  mode: "assistant" | "coaching";
  open: (ctx?: AiCtx) => void;
  close: () => void;
  setWide: (wide: boolean) => void;
  setMode: (m: "assistant" | "coaching") => void;
  setContext: (c: AiCtx) => void;
};

export const useAi = create<State>((set) => ({
  isOpen: false,
  isWide: false,
  context: {},
  mode: "assistant",
  open: (ctx) => set((s) => ({ isOpen: true, context: ctx ?? s.context })),
  close: () => set({ isOpen: false, isWide: false }),
  setWide: (isWide) => set({ isWide }),
  setMode: (mode) => set({ mode }),
  setContext: (context) => set({ context }),
}));

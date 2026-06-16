import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import {
  deleteTool as apiDeleteTool,
  listTools,
  type Tool,
} from "@/lib/spira/tools-api";

// ── Shared Personal Tools state ─────────────────────────────────────────────
// One source of truth for the user's tools so a tool the AI just created shows
// up everywhere instantly (goal sub-nav, /tools page, open windows) without a
// page reload. `recordsVersion` lets an open tool window refetch its rows after
// the AI changes them.

type ToolsState = {
  tools: Tool[];
  loaded: boolean;
  loading: boolean;
  /** Per-tool counter; bump to tell an open window to refetch its records. */
  recordsVersion: Record<number, number>;
  /** Loads once (no-op if already loaded/loading). */
  ensureLoaded: () => void;
  /** Forces a fresh fetch. */
  reload: () => Promise<void>;
  /** Inserts a newly created tool at the top (idempotent by id). */
  addTool: (tool: Tool) => void;
  /** Deletes server-side then drops it locally; also closes its window. */
  removeTool: (id: number) => Promise<void>;
  /** Signals that a tool's records changed elsewhere (e.g. the AI added rows). */
  bumpRecords: (toolId: number) => void;
};

export const useTools = create<ToolsState>((set, get) => ({
  tools: [],
  loaded: false,
  loading: false,
  recordsVersion: {},

  ensureLoaded: () => {
    if (get().loaded || get().loading) return;
    set({ loading: true });
    listTools()
      .then((all) => set({ tools: all, loaded: true, loading: false }))
      .catch(() => set({ loading: false }));
  },

  reload: async () => {
    try {
      const all = await listTools();
      set({ tools: all, loaded: true });
    } catch {
      /* keep the current list on a transient error */
    }
  },

  addTool: (tool) =>
    set((s) =>
      s.tools.some((t) => t.id === tool.id) ? s : { tools: [tool, ...s.tools] },
    ),

  removeTool: async (id) => {
    await apiDeleteTool(id);
    set((s) => ({ tools: s.tools.filter((t) => t.id !== id) }));
    useToolWindows.getState().close(id);
  },

  bumpRecords: (toolId) =>
    set((s) => ({
      recordsVersion: {
        ...s.recordsVersion,
        [toolId]: (s.recordsVersion[toolId] ?? 0) + 1,
      },
    })),
}));

// ── Floating tool windows ───────────────────────────────────────────────────
// Tools open as floating, draggable, resizable, minimizable windows that
// coexist with the AI chat (no backdrop, never modal) so the user can edit a
// tracker and watch the conversation at once. One window per tool (keyed by
// tool id); opening an already-open tool just restores and focuses it.

export type ToolWindowState = {
  id: number; // tool id
  x: number;
  y: number;
  w: number;
  h: number;
  minimized: boolean;
  /** Pinned windows keep their size/position and reappear after a reload. */
  pinned: boolean;
  z: number;
};

const DEFAULT_W = 400;
const DEFAULT_H = 480;
const MIN_W = 280;
const MIN_H = 200;

/** A cascading position anchored to the right so windows clear a left-docked chat. */
function nextRect(count: number): {
  x: number;
  y: number;
  w: number;
  h: number;
} {
  const vw = typeof window !== "undefined" ? window.innerWidth : 1280;
  const vh = typeof window !== "undefined" ? window.innerHeight : 800;
  const w = Math.min(DEFAULT_W, Math.max(MIN_W, vw - 48));
  const h = Math.min(DEFAULT_H, Math.max(MIN_H, vh - 160));
  const step = 28 * (count % 6);
  const x = Math.max(24, vw - w - 40 - step);
  const y = Math.max(80, 96 + step);
  return { x, y, w, h };
}

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v));
}

type WindowsState = {
  windows: ToolWindowState[];
  topZ: number;
  open: (toolId: number) => void;
  close: (toolId: number) => void;
  focus: (toolId: number) => void;
  toggleMinimize: (toolId: number) => void;
  togglePin: (toolId: number) => void;
  setRect: (
    toolId: number,
    rect: Partial<Pick<ToolWindowState, "x" | "y" | "w" | "h">>,
  ) => void;
};

export const useToolWindows = create<WindowsState>()(
  persist(
    (set, get) => ({
      windows: [],
      topZ: 1,

      open: (toolId) => {
        const existing = get().windows.find((w) => w.id === toolId);
        const z = get().topZ + 1;
        if (existing) {
          set((s) => ({
            topZ: z,
            windows: s.windows.map((w) =>
              w.id === toolId ? { ...w, minimized: false, z } : w,
            ),
          }));
          return;
        }
        const rect = nextRect(get().windows.length);
        set((s) => ({
          topZ: z,
          windows: [
            ...s.windows,
            { id: toolId, ...rect, minimized: false, pinned: false, z },
          ],
        }));
      },

      close: (toolId) =>
        set((s) => ({ windows: s.windows.filter((w) => w.id !== toolId) })),

      focus: (toolId) => {
        const z = get().topZ + 1;
        set((s) => ({
          topZ: z,
          windows: s.windows.map((w) => (w.id === toolId ? { ...w, z } : w)),
        }));
      },

      toggleMinimize: (toolId) =>
        set((s) => ({
          windows: s.windows.map((w) =>
            w.id === toolId ? { ...w, minimized: !w.minimized } : w,
          ),
        })),

      togglePin: (toolId) =>
        set((s) => ({
          windows: s.windows.map((w) =>
            w.id === toolId ? { ...w, pinned: !w.pinned } : w,
          ),
        })),

      setRect: (toolId, rect) =>
        set((s) => ({
          windows: s.windows.map((w) => {
            if (w.id !== toolId) return w;
            const vw = typeof window !== "undefined" ? window.innerWidth : 1280;
            const vh = typeof window !== "undefined" ? window.innerHeight : 800;
            const w2 = rect.w != null ? clamp(rect.w, MIN_W, vw) : w.w;
            const h2 = rect.h != null ? clamp(rect.h, MIN_H, vh) : w.h;
            const x2 = rect.x != null ? clamp(rect.x, 0, vw - 80) : w.x;
            const y2 = rect.y != null ? clamp(rect.y, 0, vh - 48) : w.y;
            return { ...w, x: x2, y: y2, w: w2, h: h2 };
          }),
        })),
    }),
    {
      name: "spira-tool-windows",
      storage: createJSONStorage(() => localStorage),
      // Only pinned windows survive a reload; session windows are not persisted.
      partialize: (s) => ({
        windows: s.windows.filter((w) => w.pinned),
        topZ: s.topZ,
      }),
    },
  ),
);

export const TOOL_WINDOW_LIMITS = { MIN_W, MIN_H };

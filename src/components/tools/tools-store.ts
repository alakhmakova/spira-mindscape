import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import {
  deleteTool as apiDeleteTool,
  listTools,
  type Tool,
} from "@/lib/spira/tools-api";
import { useAuth } from "@/lib/spira/auth";

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
  /** Replaces a tool in the list (e.g. after an approved AI structure change),
   *  refreshing any open window so the new schema renders at once. */
  applyToolUpdate: (tool: Tool) => void;
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

  applyToolUpdate: (tool) => {
    set((s) => ({
      tools: s.tools.map((t) => (t.id === tool.id ? tool : t)),
    }));
    // Force the open window's renderer to remount with the new schema.
    get().bumpRecords(tool.id);
  },

  removeTool: async (id) => {
    await apiDeleteTool(id);
    set((s) => ({ tools: s.tools.filter((t) => t.id !== id) }));
    useToolWindows.getState().close(id);
    useToolPins.getState().remove(id); // drop any pin for the deleted tool
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
  z: number;
};

/** Per-device cached geometry for a tool's window. */
type Geo = { x: number; y: number; w: number; h: number; minimized: boolean };

const DEFAULT_W = 400;
const DEFAULT_H = 480;
const MIN_W = 280;
const MIN_H = 200;

function viewport(): { vw: number; vh: number } {
  return {
    vw: typeof window !== "undefined" ? window.innerWidth : 1280,
    vh: typeof window !== "undefined" ? window.innerHeight : 800,
  };
}

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v));
}

/** Keep a window fully on the current screen (fixes a pinned window restored
 *  from a wider viewport, e.g. desktop → laptop). */
function clampGeo(geo: Geo): Geo {
  const { vw, vh } = viewport();
  return {
    w: clamp(geo.w, MIN_W, vw),
    h: clamp(geo.h, MIN_H, vh),
    x: clamp(geo.x, 0, Math.max(0, vw - 80)),
    y: clamp(geo.y, 0, Math.max(0, vh - 48)),
    minimized: geo.minimized,
  };
}

/** A cascading position anchored to the right so windows clear a left-docked chat. */
function nextGeo(count: number): Geo {
  const { vw, vh } = viewport();
  const w = Math.min(DEFAULT_W, Math.max(MIN_W, vw - 48));
  const h = Math.min(DEFAULT_H, Math.max(MIN_H, vh - 160));
  const step = 28 * (count % 6);
  const x = Math.max(24, vw - w - 40 - step);
  const y = Math.max(80, 96 + step);
  return { x, y, w, h, minimized: false };
}

type WindowsState = {
  windows: ToolWindowState[];
  /** Device-local geometry cache, keyed by tool id (the only persisted part). */
  geometry: Record<number, Geo>;
  topZ: number;
  open: (toolId: number) => void;
  close: (toolId: number) => void;
  focus: (toolId: number) => void;
  toggleMinimize: (toolId: number) => void;
  setRect: (
    toolId: number,
    rect: Partial<Pick<ToolWindowState, "x" | "y" | "w" | "h">>,
  ) => void;
};

export const useToolWindows = create<WindowsState>()(
  persist(
    (set, get) => ({
      windows: [],
      geometry: {},
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
        const geo = clampGeo(
          get().geometry[toolId] ?? nextGeo(get().windows.length),
        );
        set((s) => ({
          topZ: z,
          geometry: { ...s.geometry, [toolId]: geo },
          windows: [...s.windows, { id: toolId, ...geo, z }],
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
        set((s) => {
          let geometry = s.geometry;
          const windows = s.windows.map((w) => {
            if (w.id !== toolId) return w;
            const next = { ...w, minimized: !w.minimized };
            geometry = { ...geometry, [toolId]: geoOf(next) };
            return next;
          });
          return { windows, geometry };
        }),

      setRect: (toolId, rect) =>
        set((s) => {
          const cur = s.windows.find((w) => w.id === toolId);
          if (!cur) return s;
          const merged = clampGeo({
            x: rect.x ?? cur.x,
            y: rect.y ?? cur.y,
            w: rect.w ?? cur.w,
            h: rect.h ?? cur.h,
            minimized: cur.minimized,
          });
          return {
            windows: s.windows.map((w) =>
              w.id === toolId ? { ...w, ...merged } : w,
            ),
            geometry: { ...s.geometry, [toolId]: merged },
          };
        }),
    }),
    {
      name: "spira-tool-windows",
      version: 2,
      storage: createJSONStorage(() => localStorage),
      // Persist ONLY the per-device geometry cache (+ z counter). Open windows
      // are session-only, so nothing leaks across reloads or accounts.
      partialize: (s) => ({ geometry: s.geometry, topZ: s.topZ }),
      // v1 persisted the full `windows` array in localStorage; now only the
      // geometry cache is persisted. Carry over any old window positions as
      // geometry, then forget the rest.
      migrate: (persisted, version) => {
        if (version >= 2)
          return persisted as { geometry: Record<number, Geo>; topZ: number };
        const old = persisted as
          | { windows?: ToolWindowState[]; topZ?: number }
          | undefined;
        const geometry: Record<number, Geo> = {};
        for (const w of old?.windows ?? []) geometry[w.id] = geoOf(w);
        return { geometry, topZ: old?.topZ ?? 1 };
      },
    },
  ),
);

function geoOf(w: ToolWindowState): Geo {
  return { x: w.x, y: w.y, w: w.w, h: w.h, minimized: w.minimized };
}

// Reset per-user state when the signed-in account changes (login / logout /
// switch) so one account's tools and open windows never leak into another's
// session. Geometry (device-local, keyed by tool id) is harmless to keep.
let lastUserId: number | null = useAuth.getState().user?.id ?? null;
useAuth.subscribe((s) => {
  const id = s.user?.id ?? null;
  if (id === lastUserId) return;
  lastUserId = id;
  useTools.setState({
    tools: [],
    loaded: false,
    loading: false,
    recordsVersion: {},
  });
  useToolWindows.setState({ windows: [] });
});

export const TOOL_WINDOW_LIMITS = { MIN_W, MIN_H };

// ── Pinned tools (list ordering) ────────────────────────────────────────────
// Lets the user pin favourite tools to the top of the Tools list. The ARRAY
// ORDER is the pin order: pinnedIds[0] is pin #1, [1] is #2, … so the list can
// show a number rather than an ambiguous "pinned" mark. Device-local
// (localStorage) — a personal ordering preference, not shared data.

type ToolPinsState = {
  pinnedIds: number[];
  toggle: (id: number) => void;
  remove: (id: number) => void;
};

export const useToolPins = create<ToolPinsState>()(
  persist(
    (set) => ({
      pinnedIds: [],
      toggle: (id) =>
        set((s) => ({
          pinnedIds: s.pinnedIds.includes(id)
            ? s.pinnedIds.filter((x) => x !== id)
            : [...s.pinnedIds, id],
        })),
      remove: (id) =>
        set((s) => ({ pinnedIds: s.pinnedIds.filter((x) => x !== id) })),
    }),
    {
      name: "spira-tool-pins",
      storage: createJSONStorage(() => localStorage),
    },
  ),
);

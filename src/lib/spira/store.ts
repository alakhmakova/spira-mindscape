import { create } from "zustand";
import { persist } from "zustand/middleware";
import type {
  ChatMessage,
  Confidence,
  Goal,
  Option,
  Resource,
  Target,
} from "./types";

const uid = () => Math.random().toString(36).slice(2, 10);

const seed: Goal[] = [
  {
    id: uid(),
    title: "Land a senior product role at a thoughtful company",
    description:
      "Move into a senior PM position where I shape product strategy and work with a team I respect. Salary band at or above current, remote-friendly, mission-aligned.",
    confidence: 6,
    deadline: new Date(Date.now() + 1000 * 60 * 60 * 24 * 75).toISOString(),
    createdAt: new Date().toISOString(),
    reality: {
      actions: [
        { id: uid(), text: "Updated CV and portfolio site" },
        { id: uid(), text: "Reached out to 8 people in network" },
      ],
      obstacles: [
        { id: uid(), text: "Limited public writing / track record" },
        { id: uid(), text: "Time pressure from current role" },
      ],
    },
    options: [
      { id: uid(), text: "Focused outbound to 30 target companies", selected: true },
      { id: uid(), text: "Lean on network referrals only", selected: false },
      { id: uid(), text: "Build in public for 60 days, then apply", selected: false },
    ],
    resources: [
      {
        id: uid(),
        type: "note",
        title: "Interview prep notes",
        body: "STAR stories: ambiguous problem, leading without authority, shipped under constraint.",
      },
      { id: uid(), type: "link", title: "Target companies sheet", url: "https://example.com" },
    ],
    targets: [
      {
        id: uid(),
        type: "numeric",
        title: "Outbound applications",
        current: 12,
        total: 40,
        unit: "apps",
      },
      {
        id: uid(),
        type: "checklist",
        title: "Portfolio refresh",
        items: [
          { id: uid(), text: "Rewrite case study A", done: true },
          { id: uid(), text: "Rewrite case study B", done: true },
          { id: uid(), text: "Add new case study C", done: false },
          { id: uid(), text: "Polish hero section", done: false },
        ],
      },
      { id: uid(), type: "binary", title: "Pass first onsite", done: false },
    ],
  },
  {
    id: uid(),
    title: "Run a half-marathon under 1h45",
    description:
      "Build aerobic base, run 4x/week, follow a 12-week plan. Race day in spring.",
    confidence: 8,
    deadline: new Date(Date.now() + 1000 * 60 * 60 * 24 * 110).toISOString(),
    createdAt: new Date().toISOString(),
    reality: {
      actions: [{ id: uid(), text: "Completed week 1 of plan" }],
      obstacles: [{ id: uid(), text: "Old knee niggle on long runs" }],
    },
    options: [
      { id: uid(), text: "Hanson method", selected: true },
      { id: uid(), text: "Pfitzinger 12/47", selected: false },
    ],
    resources: [],
    targets: [
      { id: uid(), type: "numeric", title: "Weekly km", current: 28, total: 50, unit: "km" },
      { id: uid(), type: "binary", title: "Tune-up 10k race", done: false },
    ],
  },
  {
    id: uid(),
    title: "Ship Spira v1 to first 50 users",
    description: "Closed beta with engaged early users; iterate weekly.",
    confidence: 4,
    deadline: new Date(Date.now() + 1000 * 60 * 60 * 24 * 45).toISOString(),
    createdAt: new Date().toISOString(),
    reality: { actions: [], obstacles: [{ id: uid(), text: "Solo, limited dev time" }] },
    options: [
      { id: uid(), text: "Public waitlist + invites", selected: true },
      { id: uid(), text: "Direct outreach to 50 people", selected: false },
    ],
    resources: [],
    targets: [
      { id: uid(), type: "numeric", title: "Active users", current: 6, total: 50, unit: "users" },
      {
        id: uid(),
        type: "checklist",
        title: "Launch checklist",
        items: [
          { id: uid(), text: "Onboarding flow", done: false },
          { id: uid(), text: "Privacy page", done: false },
          { id: uid(), text: "Feedback loop", done: true },
        ],
      },
    ],
  },
];

type State = {
  goals: Goal[];
  chat: ChatMessage[];
  // goal CRUD
  addGoal: (g: Partial<Goal> & { title: string }) => string;
  updateGoal: (id: string, patch: Partial<Goal>) => void;
  deleteGoal: (id: string) => void;
  setConfidence: (id: string, c: Confidence) => void;
  // reality
  addReality: (id: string, kind: "actions" | "obstacles", text: string) => void;
  updateReality: (
    id: string,
    kind: "actions" | "obstacles",
    itemId: string,
    text: string,
  ) => void;
  removeReality: (id: string, kind: "actions" | "obstacles", itemId: string) => void;
  // options
  addOption: (id: string, text: string) => void;
  updateOption: (id: string, optId: string, patch: Partial<Option>) => void;
  selectOption: (id: string, optId: string) => void;
  removeOption: (id: string, optId: string) => void;
  reorderOptions: (id: string, from: number, to: number) => void;
  // targets
  addTarget: (id: string, t: Omit<Target, "id">) => void;
  updateTarget: (id: string, targetId: string, patch: Partial<Target>) => void;
  removeTarget: (id: string, targetId: string) => void;
  // resources
  addResource: (id: string, r: Omit<Resource, "id">) => void;
  updateResource: (id: string, rId: string, patch: Partial<Resource>) => void;
  removeResource: (id: string, rId: string) => void;
  // chat
  addChatMessage: (m: Omit<ChatMessage, "id" | "createdAt">) => void;
  resolveAction: (msgId: string, status: "approved" | "rejected") => void;
  clearChat: () => void;
};

export const useSpira = create<State>()(
  persist(
    (set) => ({
      goals: seed,
      chat: [],
      addGoal: (g) => {
        const id = uid();
        const goal: Goal = {
          id,
          title: g.title,
          description: g.description ?? "",
          confidence: (g.confidence ?? 5) as Confidence,
          deadline: g.deadline,
          createdAt: new Date().toISOString(),
          reality: g.reality ?? { actions: [], obstacles: [] },
          options: g.options ?? [],
          resources: g.resources ?? [],
          targets: g.targets ?? [],
        };
        set((s) => ({ goals: [goal, ...s.goals] }));
        return id;
      },
      updateGoal: (id, patch) =>
        set((s) => ({
          goals: s.goals.map((g) => (g.id === id ? { ...g, ...patch } : g)),
        })),
      deleteGoal: (id) => set((s) => ({ goals: s.goals.filter((g) => g.id !== id) })),
      setConfidence: (id, c) =>
        set((s) => ({
          goals: s.goals.map((g) => (g.id === id ? { ...g, confidence: c } : g)),
        })),
      addReality: (id, kind, text) =>
        set((s) => ({
          goals: s.goals.map((g) =>
            g.id === id
              ? {
                  ...g,
                  reality: {
                    ...g.reality,
                    [kind]: [...g.reality[kind], { id: uid(), text }],
                  },
                }
              : g,
          ),
        })),
      updateReality: (id, kind, itemId, text) =>
        set((s) => ({
          goals: s.goals.map((g) =>
            g.id === id
              ? {
                  ...g,
                  reality: {
                    ...g.reality,
                    [kind]: g.reality[kind].map((i) =>
                      i.id === itemId ? { ...i, text } : i,
                    ),
                  },
                }
              : g,
          ),
        })),
      removeReality: (id, kind, itemId) =>
        set((s) => ({
          goals: s.goals.map((g) =>
            g.id === id
              ? {
                  ...g,
                  reality: {
                    ...g.reality,
                    [kind]: g.reality[kind].filter((i) => i.id !== itemId),
                  },
                }
              : g,
          ),
        })),
      addOption: (id, text) =>
        set((s) => ({
          goals: s.goals.map((g) =>
            g.id === id
              ? { ...g, options: [...g.options, { id: uid(), text, selected: false }] }
              : g,
          ),
        })),
      updateOption: (id, optId, patch) =>
        set((s) => ({
          goals: s.goals.map((g) =>
            g.id === id
              ? {
                  ...g,
                  options: g.options.map((o) => (o.id === optId ? { ...o, ...patch } : o)),
                }
              : g,
          ),
        })),
      selectOption: (id, optId) =>
        set((s) => ({
          goals: s.goals.map((g) =>
            g.id === id
              ? {
                  ...g,
                  options: g.options.map((o) => ({ ...o, selected: o.id === optId })),
                }
              : g,
          ),
        })),
      removeOption: (id, optId) =>
        set((s) => ({
          goals: s.goals.map((g) =>
            g.id === id ? { ...g, options: g.options.filter((o) => o.id !== optId) } : g,
          ),
        })),
      reorderOptions: (id, from, to) =>
        set((s) => ({
          goals: s.goals.map((g) => {
            if (g.id !== id) return g;
            const arr = [...g.options];
            const [m] = arr.splice(from, 1);
            arr.splice(to, 0, m);
            return { ...g, options: arr };
          }),
        })),
      addTarget: (id, t) =>
        set((s) => ({
          goals: s.goals.map((g) =>
            g.id === id ? { ...g, targets: [...g.targets, { ...t, id: uid() } as Target] } : g,
          ),
        })),
      updateTarget: (id, targetId, patch) =>
        set((s) => ({
          goals: s.goals.map((g) =>
            g.id === id
              ? {
                  ...g,
                  targets: g.targets.map((t) =>
                    t.id === targetId ? ({ ...t, ...patch } as Target) : t,
                  ),
                }
              : g,
          ),
        })),
      removeTarget: (id, targetId) =>
        set((s) => ({
          goals: s.goals.map((g) =>
            g.id === id ? { ...g, targets: g.targets.filter((t) => t.id !== targetId) } : g,
          ),
        })),
      addResource: (id, r) =>
        set((s) => ({
          goals: s.goals.map((g) =>
            g.id === id
              ? { ...g, resources: [...g.resources, { ...r, id: uid() } as Resource] }
              : g,
          ),
        })),
      updateResource: (id, rId, patch) =>
        set((s) => ({
          goals: s.goals.map((g) =>
            g.id === id
              ? {
                  ...g,
                  resources: g.resources.map((r) =>
                    r.id === rId ? ({ ...r, ...patch } as Resource) : r,
                  ),
                }
              : g,
          ),
        })),
      removeResource: (id, rId) =>
        set((s) => ({
          goals: s.goals.map((g) =>
            g.id === id ? { ...g, resources: g.resources.filter((r) => r.id !== rId) } : g,
          ),
        })),
      addChatMessage: (m) =>
        set((s) => ({
          chat: [
            ...s.chat,
            { ...m, id: uid(), createdAt: new Date().toISOString() } as ChatMessage,
          ],
        })),
      resolveAction: (msgId, status) =>
        set((s) => ({
          chat: s.chat.map((m) =>
            m.id === msgId && m.action ? { ...m, action: { ...m.action, status } } : m,
          ),
        })),
      clearChat: () => set({ chat: [] }),
    }),
    { name: "spira-store-v1" },
  ),
);

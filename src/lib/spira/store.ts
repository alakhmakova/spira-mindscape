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
let seedCounter = 0;
const seedUid = () => `seed-${++seedCounter}`;
const seedDate = (days: number) =>
  new Date(Date.UTC(2026, 0, 1 + days, 12, 0, 0)).toISOString();

function migratePersistedState(persisted: unknown) {
  if (!persisted || typeof persisted !== "object") return persisted;
  const state = persisted as { goals?: Goal[] };
  if (!Array.isArray(state.goals)) return persisted;

  return {
    ...state,
    goals: state.goals.map((goal) => ({
      ...goal,
      resources: goal.resources.map((resource) =>
        (resource as { type?: string }).type === "contact"
          ? ({ ...resource, type: "email" } as Resource)
          : resource,
      ),
    })),
  };
}

const seed: Goal[] = [
  {
    id: seedUid(),
    title: "Land a senior product role at a thoughtful company",
    description:
      "Move into a senior PM position where I shape product strategy and work with a team I respect. Salary band at or above current, remote-friendly, mission-aligned.",
    confidence: 6,
    deadline: seedDate(75),
    createdAt: seedDate(0),
    reality: {
      actions: [
        { id: seedUid(), text: "Updated CV and portfolio site" },
        { id: seedUid(), text: "Reached out to 8 people in network" },
      ],
      obstacles: [
        { id: seedUid(), text: "Limited public writing / track record" },
        { id: seedUid(), text: "Time pressure from current role" },
      ],
    },
    options: [
      { id: seedUid(), text: "Focused outbound to 30 target companies", selected: true },
      { id: seedUid(), text: "Lean on network referrals only", selected: false },
      { id: seedUid(), text: "Build in public for 60 days, then apply", selected: false },
    ],
    resources: [
      {
        id: seedUid(),
        type: "note",
        title: "Interview prep notes",
        body: "STAR stories: ambiguous problem, leading without authority, shipped under constraint.",
      },
      { id: seedUid(), type: "link", title: "Target companies sheet", url: "https://example.com" },
    ],
    targets: [
      {
        id: seedUid(),
        type: "numeric",
        title: "Outbound applications",
        current: 12,
        total: 40,
        unit: "apps",
      },
      {
        id: seedUid(),
        type: "checklist",
        title: "Portfolio refresh",
        items: [
          { id: seedUid(), text: "Rewrite case study A", done: true, achievedAt: seedDate(5) },
          { id: seedUid(), text: "Rewrite case study B", done: true, achievedAt: seedDate(10) },
          { id: seedUid(), text: "Add new case study C", done: false },
          { id: seedUid(), text: "Polish hero section", done: false },
        ],
      },
      { id: seedUid(), type: "binary", title: "Pass first onsite", done: false },
    ],
  },
  {
    id: seedUid(),
    title: "Run a half-marathon under 1h45",
    description:
      "Build aerobic base, run 4x/week, follow a 12-week plan. Race day in spring.",
    confidence: 8,
    deadline: seedDate(110),
    createdAt: seedDate(0),
    reality: {
      actions: [{ id: seedUid(), text: "Completed week 1 of plan" }],
      obstacles: [{ id: seedUid(), text: "Old knee niggle on long runs" }],
    },
    options: [
      { id: seedUid(), text: "Hanson method", selected: true },
      { id: seedUid(), text: "Pfitzinger 12/47", selected: false },
    ],
    resources: [],
    targets: [
      { id: seedUid(), type: "numeric", title: "Weekly km", current: 28, total: 50, unit: "km" },
      { id: seedUid(), type: "binary", title: "Tune-up 10k race", done: false },
    ],
  },
  {
    id: seedUid(),
    title: "Ship Spira v1 to first 50 users",
    description: "Closed beta with engaged early users; iterate weekly.",
    confidence: 4,
    deadline: seedDate(45),
    createdAt: seedDate(0),
    reality: { actions: [], obstacles: [{ id: seedUid(), text: "Solo, limited dev time" }] },
    options: [
      { id: seedUid(), text: "Public waitlist + invites", selected: true },
      { id: seedUid(), text: "Direct outreach to 50 people", selected: false },
    ],
    resources: [],
    targets: [
      { id: seedUid(), type: "numeric", title: "Active users", current: 6, total: 50, unit: "users" },
      {
        id: seedUid(),
        type: "checklist",
        title: "Launch checklist",
        items: [
          { id: seedUid(), text: "Onboarding flow", done: false },
          { id: seedUid(), text: "Privacy page", done: false },
          { id: seedUid(), text: "Feedback loop", done: true, achievedAt: seedDate(12) },
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
          goals: s.goals.map((g) => {
            if (g.id !== id) return g;
            const updated = { ...g, ...patch };
            // If goal just reached 100% or is explicitly achieved
            if (patch.achievedAt === undefined && !g.achievedAt) {
               // Logic to detect 100% progress could go here, but let's stick to manual/patch for now
            }
            return updated;
          }),
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
                    [kind]: [{ id: uid(), text }, ...g.reality[kind]],
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
                  targets: g.targets.map((t) => {
                    if (t.id !== targetId) return t;
                    const next = { ...t, ...patch } as Target;
                    
                    // Auto-set achievedAt if status changed to done/100%
                    if (next.type === "binary" && t.type === "binary" && next.done && !t.done) {
                       next.achievedAt = new Date().toISOString();
                    } else if (next.type === "numeric" && t.type === "numeric" && next.current >= next.total && t.current < t.total) {
                       next.achievedAt = new Date().toISOString();
                    } else if (next.type === "checklist" && t.type === "checklist" && (patch as any).items) {
                       next.items = next.items.map(item => {
                          const prev = t.items.find((pi) => pi.id === item.id);
                          if (item.done && (!prev || !prev.done)) {
                             return { ...item, achievedAt: new Date().toISOString() };
                          }
                          return item;
                       });
                    }
                    
                    return next;
                  }),
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
    { name: "spira-store-v1", version: 2, migrate: migratePersistedState },
  ),
);

import { create } from "zustand";
import { SpiraApiError, spiraApi } from "./api";
import { goalProgress, targetProgress } from "./progress";
import type {
  ChatMessage,
  Confidence,
  Goal,
  Option,
  Resource,
  ResourceInput,
  Target,
} from "./types";

const localId = () => `local-${Math.random().toString(36).slice(2, 10)}`;

type CreateTargetInput =
  | Omit<Extract<Target, { type: "numeric" }>, "id" | "current">
  | Omit<Extract<Target, { type: "binary" }>, "id">
  | Omit<Extract<Target, { type: "checklist" }>, "id">;
const nowIso = () => new Date().toISOString();

type RealityKind = "actions" | "obstacles";

type State = {
  goals: Goal[];
  chat: ChatMessage[];
  isLoading: boolean;
  hasLoaded: boolean;
  syncError?: string;
  syncErrorKind?: "network" | "service";
  loadGoals: () => Promise<void>;
  refreshGoals: () => Promise<void>;
  clearSyncError: () => void;
  addGoal: (g: Partial<Goal> & { title: string }, onCreated?: (goal: Goal) => void) => string;
  updateGoal: (id: string, patch: Partial<Goal>) => void;
  deleteGoal: (id: string) => void;
  setConfidence: (id: string, c: Confidence) => void;
  addReality: (id: string, kind: RealityKind, text: string) => void;
  updateReality: (
    id: string,
    kind: RealityKind,
    itemId: string,
    text: string,
  ) => void;
  removeReality: (id: string, kind: RealityKind, itemId: string) => void;
  addOption: (id: string, text: string, onCreated?: (created: Option) => void) => void;
  updateOption: (id: string, optId: string, patch: Partial<Option>) => void;
  selectOption: (id: string, optId: string) => void;
  removeOption: (id: string, optId: string) => void;
  reorderOptions: (id: string, from: number, to: number) => void;
  addTarget: (id: string, t: CreateTargetInput) => Promise<Target | undefined>;
  updateTarget: (id: string, targetId: string, patch: Partial<Target>) => void;
  removeTarget: (id: string, targetId: string) => void;
  addResource: (id: string, r: ResourceInput, onCreated?: (created: Resource) => void) => string;
  updateResource: (id: string, rId: string, patch: Partial<Resource>) => void;
  removeResource: (id: string, rId: string) => void;
  addChatMessage: (m: Omit<ChatMessage, "id" | "createdAt">) => void;
  resolveAction: (msgId: string, status: "approved" | "rejected") => void;
  clearChat: () => void;
};

const syncTimers = new Map<string, ReturnType<typeof setTimeout>>();

function debounceRemote(key: string, task: () => Promise<void>) {
  const existing = syncTimers.get(key);
  if (existing) {
    clearTimeout(existing);
  }
  syncTimers.set(
    key,
    setTimeout(() => {
      syncTimers.delete(key);
      void task();
    }, 500),
  );
}

function replaceGoal(goals: Goal[], goalId: string, next: Goal) {
  return goals.map((goal) => (goal.id === goalId ? next : goal));
}

function updateGoalInList(
  goals: Goal[],
  goalId: string,
  updater: (goal: Goal) => Goal,
) {
  return goals.map((goal) => (goal.id === goalId ? updater(goal) : goal));
}

function mergeGoalPatch(goal: Goal, patch: Partial<Goal>): Goal {
  const updated = { ...goal, ...patch };
  if (patch.achievedAt === undefined) {
    if (goalProgress(updated) >= 1) {
      updated.achievedAt = updated.achievedAt ?? nowIso();
    } else {
      updated.achievedAt = undefined;
    }
  }
  return updated;
}

function mergeTargetPatch(target: Target, patch: Partial<Target>): Target {
  const next = { ...target, ...patch } as Target;

  if (
    next.type === "checklist" &&
    target.type === "checklist" &&
    "items" in patch
  ) {
    next.items = next.items.map((item) => {
      const previous = target.items.find(
        (candidate) => candidate.id === item.id,
      );
      if (item.done && (!previous || !previous.done)) {
        return { ...item, achievedAt: item.achievedAt ?? nowIso() };
      }
      if (!item.done && previous?.done) {
        return { ...item, achievedAt: undefined };
      }
      return item;
    });
  }

  if (patch.achievedAt === undefined) {
    if (targetProgress(next) >= 1) {
      next.achievedAt = next.achievedAt ?? nowIso();
    } else {
      next.achievedAt = undefined;
    }
  }

  return next;
}

function updateGoalAchievementFromProgress(goal: Goal): Goal {
  if (goalProgress(goal) >= 1) {
    return { ...goal, achievedAt: goal.achievedAt ?? nowIso() };
  }
  return { ...goal, achievedAt: undefined };
}

function setSyncError(set: (state: Partial<State>) => void, error: unknown) {
  console.error("Spira sync failed", error);
  // Session expired mid-use: a generic "sync failed" here would let the user
  // keep editing while every save silently dies with 401. Hard-redirect to
  // login instead (mirrors loadGoals) so they re-authenticate and lose at
  // most the one change that just failed.
  if (error instanceof SpiraApiError && error.status === 401) {
    window.location.replace("/login");
    return;
  }
  const kind = error instanceof SpiraApiError ? error.kind : "service";
  set({
    syncError:
      error instanceof SpiraApiError
        ? error.message
        : "Something went wrong. Please try again in a moment.",
    syncErrorKind: kind,
  });
}

export const useSpira = create<State>()((set, get) => ({
  goals: [],
  chat: [],
  isLoading: false,
  hasLoaded: false,
  syncError: undefined,
  syncErrorKind: undefined,

  loadGoals: async () => {
    if (get().isLoading || get().hasLoaded) return;
    set({ isLoading: true, syncError: undefined, syncErrorKind: undefined });
    try {
      const goals = await spiraApi.fetchGoals();
      set({
        goals,
        isLoading: false,
        hasLoaded: true,
        syncError: undefined,
        syncErrorKind: undefined,
      });
    } catch (error) {
      // Session expired mid-use — do a hard redirect so auth state is fully
      // reset (no partial React state to clean up).
      if (error instanceof SpiraApiError && error.status === 401) {
        window.location.replace("/login");
        return;
      }
      console.error("Spira sync failed", error);
      const kind = error instanceof SpiraApiError ? error.kind : "service";
      set({
        isLoading: false,
        hasLoaded: true,
        syncError:
          error instanceof SpiraApiError
            ? error.message
            : "Something went wrong. Please try again in a moment.",
        syncErrorKind: kind,
      });
    }
  },

  refreshGoals: async () => {
    set({ isLoading: true, syncError: undefined, syncErrorKind: undefined });
    try {
      const goals = await spiraApi.fetchGoals();
      set({
        goals,
        isLoading: false,
        hasLoaded: true,
        syncError: undefined,
        syncErrorKind: undefined,
      });
    } catch (error) {
      console.error("Spira sync failed", error);
      const kind = error instanceof SpiraApiError ? error.kind : "service";
      set({
        isLoading: false,
        hasLoaded: true,
        syncError:
          error instanceof SpiraApiError
            ? error.message
            : "Something went wrong. Please try again in a moment.",
        syncErrorKind: kind,
      });
    }
  },

  clearSyncError: () => set({ syncError: undefined, syncErrorKind: undefined }),

  addGoal: (g, onCreated) => {
    const tempId = localId();
    const goal: Goal = {
      id: tempId,
      title: g.title,
      description: g.description ?? "",
      confidence: (g.confidence ?? 5) as Confidence,
      deadline: g.deadline,
      createdAt: nowIso(),
      achievedAt: g.achievedAt,
      reality: g.reality ?? { actions: [], obstacles: [] },
      options: g.options ?? [],
      resources: g.resources ?? [],
      targets: g.targets ?? [],
      confidenceHistory: [{ value: (g.confidence ?? 5), at: nowIso() }],
    };

    set((state) => ({ goals: [goal, ...state.goals], syncError: undefined }));
    void spiraApi
      .createGoal({
        title: goal.title,
        description: goal.description,
        confidence: goal.confidence,
        deadline: goal.deadline,
      })
      .then((created) => {
        set((state) => ({
          goals: replaceGoal(state.goals, tempId, created),
        }));
        // Hand back the persisted goal (with its real id) so callers can, e.g.,
        // offer an "Open goal" shortcut that navigates to the right route.
        onCreated?.(created);
      })
      .catch((error) => {
        set((state) => ({
          goals: state.goals.filter((item) => item.id !== tempId),
        }));
        setSyncError(set, error);
      });
    return tempId;
  },

  updateGoal: (id, patch) => {
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => {
        const merged = mergeGoalPatch(goal, patch);
        // Record a confidence-history point optimistically whenever confidence
        // actually changes — covers both the manual control and AI proposals,
        // which both route through updateGoal. The server records the same
        // entry; a full reload replaces these with the authoritative list.
        if (patch.confidence != null && patch.confidence !== goal.confidence) {
          merged.confidenceHistory = [
            { value: patch.confidence, at: nowIso() },
            ...(goal.confidenceHistory ?? []),
          ];
        }
        return merged;
      }),
      syncError: undefined,
    }));

    if (id.startsWith("local-")) return;
    debounceRemote(`goal:${id}`, async () => {
      const current = get().goals.find((goal) => goal.id === id);
      if (!current) return;
      try {
        await spiraApi.updateGoal(id, {
          title: current.title,
          description: current.description,
          confidence: current.confidence,
          deadline: current.deadline,
          achievedAt: current.achievedAt,
        });
      } catch (error) {
        setSyncError(set, error);
      }
    });
  },

  deleteGoal: (id) => {
    const previous = get().goals;
    set((state) => ({
      goals: state.goals.filter((goal) => goal.id !== id),
      syncError: undefined,
    }));

    if (id.startsWith("local-")) return;
    void spiraApi.deleteGoal(id).catch((error) => {
      set({ goals: previous });
      setSyncError(set, error);
    });
  },

  setConfidence: (id, confidence) => get().updateGoal(id, { confidence }),

  addReality: (id, kind, text) => {
    const item = { id: localId(), text };
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => ({
        ...goal,
        reality: {
          ...goal.reality,
          [kind]: [item, ...goal.reality[kind]],
        },
      })),
      syncError: undefined,
    }));

    if (id.startsWith("local-")) return;
    void spiraApi
      .addRealityItem(id, kind, text)
      .then((reality) =>
        // Reconcile ONLY the item we just added (swap our temp id for the server one),
        // instead of replacing the whole list. Creating several items at once fires
        // concurrent calls whose full-list responses would otherwise clobber each other
        // and leave only the last — see addOption, which already reconciles by id.
        set((state) => ({
          goals: updateGoalInList(state.goals, id, (goal) => {
            const known = new Set(
              goal.reality[kind].filter((i) => !i.id.startsWith("local-")).map((i) => i.id),
            );
            const created = reality[kind].find((si) => si.text === text && !known.has(si.id));
            let swapped = false;
            const next = goal.reality[kind].map((i) => {
              if (!swapped && i.id === item.id) { swapped = true; return created ?? i; }
              return i;
            });
            return { ...goal, reality: { ...goal.reality, [kind]: next } };
          }),
        })),
      )
      .catch((error) => setSyncError(set, error));
  },

  updateReality: (id, kind, itemId, text) => {
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => ({
        ...goal,
        reality: {
          ...goal.reality,
          [kind]: goal.reality[kind].map((item) =>
            item.id === itemId ? { ...item, text } : item,
          ),
        },
      })),
      syncError: undefined,
    }));

    if (id.startsWith("local-") || itemId.startsWith("local-")) return;
    void spiraApi
      .updateRealityItem(id, kind, itemId, text)
      .then((reality) =>
        set((state) => ({
          goals: updateGoalInList(state.goals, id, (goal) => ({
            ...goal,
            reality,
          })),
        })),
      )
      .catch((error) => setSyncError(set, error));
  },

  removeReality: (id, kind, itemId) => {
    const previous = get().goals;
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => ({
        ...goal,
        reality: {
          ...goal.reality,
          [kind]: goal.reality[kind].filter((item) => item.id !== itemId),
        },
      })),
      syncError: undefined,
    }));

    if (id.startsWith("local-") || itemId.startsWith("local-")) return;
    void spiraApi
      .removeRealityItem(id, kind, itemId)
      .then((reality) =>
        set((state) => ({
          goals: updateGoalInList(state.goals, id, (goal) => ({
            ...goal,
            reality,
          })),
        })),
      )
      .catch((error) => {
        set({ goals: previous });
        setSyncError(set, error);
      });
  },

  addOption: (id, text, onCreated) => {
    const tempId = localId();
    const option: Option = { id: tempId, text, selected: false, position: 0 };
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => ({
        ...goal,
        options: [...goal.options, option],
      })),
      syncError: undefined,
    }));

    if (id.startsWith("local-")) {
      onCreated?.(option);
      return;
    }
    void spiraApi
      .addOption(id, text)
      .then((created) => {
        set((state) => ({
          goals: updateGoalInList(state.goals, id, (goal) => ({
            ...goal,
            options: goal.options.map((item) =>
              item.id === tempId ? created : item,
            ),
          })),
        }));
        // Hand back the persisted option (with its real id) so callers can, e.g.,
        // immediately select it ("create an option and make it active").
        onCreated?.(created);
      })
      .catch((error) => setSyncError(set, error));
  },

  updateOption: (id, optId, patch) => {
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => ({
        ...goal,
        options: goal.options.map((option) =>
          option.id === optId ? { ...option, ...patch } : option,
        ),
      })),
      syncError: undefined,
    }));

    if (id.startsWith("local-") || optId.startsWith("local-")) return;
    debounceRemote(`option:${optId}`, async () => {
      const goal = get().goals.find((item) => item.id === id);
      const option = goal?.options.find((item) => item.id === optId);
      if (!option) return;
      try {
        await spiraApi.updateOption(id, optId, option);
      } catch (error) {
        setSyncError(set, error);
      }
    });
  },

  selectOption: (id, optId) => {
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => ({
        ...goal,
        options: goal.options.map((option) => ({
          ...option,
          selected: option.id === optId,
        })),
      })),
      syncError: undefined,
    }));

    if (id.startsWith("local-") || optId.startsWith("local-")) return;
    void spiraApi
      .selectOption(id, optId)
      .then(() => get().refreshGoals())
      .catch((error) => setSyncError(set, error));
  },

  removeOption: (id, optId) => {
    const previous = get().goals;
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => ({
        ...goal,
        options: goal.options.filter((option) => option.id !== optId),
      })),
      syncError: undefined,
    }));

    if (id.startsWith("local-") || optId.startsWith("local-")) return;
    void spiraApi.removeOption(id, optId).catch((error) => {
      set({ goals: previous });
      setSyncError(set, error);
    });
  },

  reorderOptions: (id, from, to) => {
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => {
        const options = [...goal.options];
        const [moved] = options.splice(from, 1);
        if (!moved) return goal;
        options.splice(to, 0, moved);
        return { ...goal, options };
      }),
    }));

    if (id.startsWith("local-")) return;
    debounceRemote(`reorderOptions:${id}`, async () => {
      const goal = get().goals.find((item) => item.id === id);
      if (!goal) return;
      const optionIds = goal.options.map((option) => option.id);
      try {
        const updated = await spiraApi.reorderOptions(id, optionIds);
        set((state) => ({
          goals: updateGoalInList(state.goals, id, (g) => ({
            ...g,
            options: updated,
          })),
        }));
      } catch (error) {
        setSyncError(set, error);
      }
    });
  },

  addTarget: (id, targetInput) => {
    const tempId = localId();
    const target = {
      ...targetInput,
      id: tempId,
      ...(targetInput.type === "numeric"
        ? { current: targetInput.start ?? 0 }
        : {}),
    } as Target;
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => ({
        ...goal,
        targets: [...goal.targets, target],
      })),
      syncError: undefined,
    }));

    if (id.startsWith("local-")) return Promise.resolve(undefined);
    // Returns the persisted target so callers can chain a follow-up update
    // (e.g. mark a freshly created target done) using its real server id.
    return spiraApi
      .createTarget(id, targetInput)
      .then((created) => {
        set((state) => ({
          goals: updateGoalInList(state.goals, id, (goal) => ({
            ...goal,
            targets: goal.targets.map((item) =>
              item.id === tempId ? created : item,
            ),
          })),
        }));
        return created;
      })
      .catch((error) => {
        setSyncError(set, error);
        return undefined;
      });
  },

  updateTarget: (id, targetId, patch) => {
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) =>
        updateGoalAchievementFromProgress({
          ...goal,
          targets: goal.targets.map((target) =>
            target.id === targetId ? mergeTargetPatch(target, patch) : target,
          ),
        }),
      ),
      syncError: undefined,
    }));

    if (id.startsWith("local-") || targetId.startsWith("local-")) return;
    debounceRemote(`target:${targetId}`, async () => {
      const goal = get().goals.find((item) => item.id === id);
      const target = goal?.targets.find((item) => item.id === targetId);
      if (!target) return;
      try {
        await spiraApi.updateTarget(targetId, target);
      } catch (error) {
        setSyncError(set, error);
      }
    });
  },

  removeTarget: (id, targetId) => {
    const previous = get().goals;
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => ({
        ...goal,
        targets: goal.targets.filter((target) => target.id !== targetId),
      })),
      syncError: undefined,
    }));

    if (id.startsWith("local-") || targetId.startsWith("local-")) return;
    void spiraApi.deleteTarget(targetId).catch((error) => {
      set({ goals: previous });
      setSyncError(set, error);
    });
  },

  addResource: (id, resourceInput, onCreated) => {
    const tempId = localId();
    const resource = { ...resourceInput, id: tempId } as Resource;
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => ({
        ...goal,
        resources: [...goal.resources, resource],
      })),
      syncError: undefined,
    }));

    if (id.startsWith("local-")) {
      onCreated?.(resource);
      return tempId;
    }
    void spiraApi
      .createResource(id, resourceInput)
      .then((created) => {
        set((state) => ({
          goals: updateGoalInList(state.goals, id, (goal) => ({
            ...goal,
            resources: goal.resources.map((item) =>
              item.id === tempId ? created : item,
            ),
          })),
        }));
        // Hand back the persisted resource (with its real id) so callers can, e.g.,
        // open a freshly created note in the full-screen editor.
        onCreated?.(created);
      })
      .catch((error) => {
        // Roll back ONLY this failed item (by its temp id) — never a whole-list restore,
        // which would wipe siblings created at the same time (e.g. adding several resources
        // at once where just one fails).
        set((state) => ({
          goals: updateGoalInList(state.goals, id, (goal) => ({
            ...goal,
            resources: goal.resources.filter((item) => item.id !== tempId),
          })),
        }));
        setSyncError(set, error);
      });
    return tempId;
  },

  updateResource: (id, resourceId, patch) => {
    const previous = get().goals;
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => ({
        ...goal,
        resources: goal.resources.map((resource) =>
          resource.id === resourceId
            ? ({ ...resource, ...patch } as Resource)
            : resource,
        ),
      })),
      syncError: undefined,
    }));

    if (id.startsWith("local-") || resourceId.startsWith("local-")) return;
    debounceRemote(`resource:${resourceId}`, async () => {
      const goal = get().goals.find((item) => item.id === id);
      const resource = goal?.resources.find((item) => item.id === resourceId);
      if (!resource) return;
      try {
        await spiraApi.updateResource(resourceId, resource);
      } catch (error) {
        set({ goals: previous });
        setSyncError(set, error);
      }
    });
  },

  removeResource: (id, resourceId) => {
    const previous = get().goals;
    set((state) => ({
      goals: updateGoalInList(state.goals, id, (goal) => ({
        ...goal,
        resources: goal.resources.filter(
          (resource) => resource.id !== resourceId,
        ),
      })),
      syncError: undefined,
    }));

    if (id.startsWith("local-") || resourceId.startsWith("local-")) return;
    void spiraApi.deleteResource(resourceId).catch((error) => {
      set({ goals: previous });
      setSyncError(set, error);
    });
  },

  addChatMessage: (message) =>
    set((state) => ({
      chat: [
        ...state.chat,
        { ...message, id: localId(), createdAt: nowIso() } as ChatMessage,
      ],
    })),

  resolveAction: (msgId, status) =>
    set((state) => ({
      chat: state.chat.map((message) =>
        message.id === msgId && message.action
          ? { ...message, action: { ...message.action, status } }
          : message,
      ),
    })),

  clearChat: () => set({ chat: [] }),
}));

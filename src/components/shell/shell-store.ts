import { create } from "zustand";

export type SortKey =
  | "recent"
  | "deadline"
  | "progress"
  | "confidence"
  | "title";
export type SortDirection = "asc" | "desc";
export type GoalStatusFilter = "all" | "achieved" | "not-achieved";

type State = {
  query: string;
  sort: SortKey;
  sortDirection: SortDirection;
  deadlineFrom: string;
  deadlineTo: string;
  confidence: string;
  status: GoalStatusFilter;
  viewMode: "cards" | "table";
  setQuery: (q: string) => void;
  setSort: (s: SortKey) => void;
  setSortDirection: (d: SortDirection) => void;
  resetSort: () => void;
  setDeadlineFrom: (value: string) => void;
  setDeadlineTo: (value: string) => void;
  setConfidence: (value: string) => void;
  setStatus: (value: GoalStatusFilter) => void;
  resetFilters: () => void;
  setViewMode: (v: "cards" | "table") => void;
};

export const useShellFilters = create<State>((set) => ({
  query: "",
  sort: "recent",
  sortDirection: "desc",
  deadlineFrom: "",
  deadlineTo: "",
  confidence: "",
  status: "all",
  viewMode: "cards",
  setQuery: (query) => set({ query }),
  setSort: (sort) => set({ sort }),
  setSortDirection: (sortDirection) => set({ sortDirection }),
  resetSort: () => set({ sort: "recent", sortDirection: "desc" }),
  setDeadlineFrom: (deadlineFrom) => set({ deadlineFrom }),
  setDeadlineTo: (deadlineTo) => set({ deadlineTo }),
  setConfidence: (confidence) => set({ confidence }),
  setStatus: (status) => set({ status }),
  resetFilters: () => set({ deadlineFrom: "", deadlineTo: "", confidence: "", status: "all" }),
  setViewMode: (viewMode) => set({ viewMode }),
}));

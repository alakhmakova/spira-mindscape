import { create } from "zustand";

export type SortKey =
  | "recent"
  | "deadline"
  | "progress"
  | "confidence"
  | "title";
export type DeadlineFilter = "all" | "overdue" | "week" | "month";
export type ConfidenceFilter = "all" | "low" | "med" | "high";

type State = {
  query: string;
  sort: SortKey;
  filterDeadline: DeadlineFilter;
  filterConfidence: ConfidenceFilter;
  setQuery: (q: string) => void;
  setSort: (s: SortKey) => void;
  setFilterDeadline: (f: DeadlineFilter) => void;
  setFilterConfidence: (f: ConfidenceFilter) => void;
};

export const useShellFilters = create<State>((set) => ({
  query: "",
  sort: "recent",
  filterDeadline: "all",
  filterConfidence: "all",
  setQuery: (query) => set({ query }),
  setSort: (sort) => set({ sort }),
  setFilterDeadline: (filterDeadline) => set({ filterDeadline }),
  setFilterConfidence: (filterConfidence) => set({ filterConfidence }),
}));

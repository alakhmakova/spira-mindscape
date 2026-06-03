export type Confidence = 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10;

export type ChecklistItem = {
  id: string;
  text: string;
  done: boolean;
  deadline?: string;
  achievedAt?: string;
};

export type Target =
  | {
      id: string;
      type: "numeric";
      title: string;
      start?: number;
      current: number;
      total: number;
      unit?: string;
      deadline?: string;
      achievedAt?: string;
    }
  | {
      id: string;
      type: "binary";
      title: string;
      done: boolean;
      deadline?: string;
      achievedAt?: string;
    }
  | {
      id: string;
      type: "checklist";
      title: string;
      items: ChecklistItem[];
      deadline?: string;
      achievedAt?: string;
    };

export type Resource =
  | { id: string; type: "note"; title: string; body: string }
  | { id: string; type: "link"; title: string; url: string }
  | { id: string; type: "file"; title: string; mime: string; dataUrl: string }
  | {
      id: string;
      type: "email";
      name: string;
      role?: string;
      email?: string;
      phone?: string;
    };

/**
 * Resource fields for creation: same union as {@link Resource} but without
 * `id` (assigned by the server).
 *
 * Plain `Omit<Resource, "id">` does NOT distribute over union members in
 * TypeScript — it collapses to only the keys common to every variant.
 * This explicit form gives each discriminant its full set of fields.
 */
export type ResourceInput =
  | Omit<Extract<Resource, { type: "note" }>, "id">
  | Omit<Extract<Resource, { type: "link" }>, "id">
  | Omit<Extract<Resource, { type: "file" }>, "id">
  | Omit<Extract<Resource, { type: "email" }>, "id">;

export type Option = {
  id: string;
  text: string;
  selected: boolean;
  position: number;
};

export type Goal = {
  id: string;
  title: string;
  description: string;
  confidence: Confidence;
  deadline?: string;
  createdAt: string;
  achievedAt?: string;
  reality: {
    actions: { id: string; text: string }[];
    obstacles: { id: string; text: string }[];
  };
  options: Option[];
  resources: Resource[];
  targets: Target[];
  /** Confidence values over time, newest first. Loaded from the server; the
   *  store also prepends an entry optimistically when confidence changes. */
  confidenceHistory?: { value: number; at: string }[];
};

export type AiAction = {
  id: string;
  goalId?: string;
  title: string;
  description: string;
  reasoning: string;
  status: "pending" | "approved" | "rejected";
};

export type ChatMessage = {
  id: string;
  role: "user" | "assistant";
  content: string;
  action?: AiAction;
  createdAt: string;
};

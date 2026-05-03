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
      weight?: number;
      achievedAt?: string;
    }
  | {
      id: string;
      type: "binary";
      title: string;
      done: boolean;
      deadline?: string;
      weight?: number;
      achievedAt?: string;
    }
  | {
      id: string;
      type: "checklist";
      title: string;
      items: ChecklistItem[];
      deadline?: string;
      weight?: number;
      achievedAt?: string;
    };

export type Resource =
  | { id: string; type: "note"; title: string; body: string }
  | { id: string; type: "link"; title: string; url: string }
  | { id: string; type: "file"; title: string; mime: string; dataUrl: string }
  | {
      id: string;
      type: "contact";
      name: string;
      role?: string;
      email?: string;
      phone?: string;
    };

export type Option = {
  id: string;
  text: string;
  selected: boolean;
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

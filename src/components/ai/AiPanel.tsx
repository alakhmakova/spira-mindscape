import { useEffect, useRef, useState, useCallback } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { X } from "lucide-react";
import {
  Drawer,
  DrawerContent,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { useIsMobile } from "@/hooks/use-mobile";
import { useAi } from "./ai-store";
import { useSpira } from "@/lib/spira/store";
import { cn } from "@/lib/utils";
import type { AiAction } from "@/lib/spira/types";
import { toast } from "sonner";
import { streamChat, saveApiKey, listApiKeys, updateKeyModel, fetchProviderModels, listGoalProposals, approveProposal, rejectProposal, type HistoryEntry } from "./ai-api";

// ── Types ─────────────────────────────────────────────────────────────────

type Msg = {
  id: string;
  role: "user" | "assistant" | "system" | "end";
  content: string;
  streaming?: boolean;
  proposals?: Proposal[];
};

type ProposalKind =
  // create / goal-level
  | "target" | "task" | "option" | "note" | "edit" | "obstacle" | "action" | "confidence" | "deadline"
  // edit existing
  | "edit_target" | "edit_option" | "edit_obstacle" | "edit_action" | "edit_note"
  // state changes
  | "complete_target" | "target_progress" | "select_option" | "checklist_item" | "add_checklist_item";

type Proposal = {
  id: string;
  kind: ProposalKind;
  title: string;
  detail?: string;
  reasoning?: string;
  status: "pending" | "approved" | "rejected";
  field?: string;       // for "edit": "title" | "description"
  body?: string;        // for "note" / "edit_note"
  deadline?: string;    // ISO date for target/task
  rawValue?: string;    // raw tool argument value (number for confidence/progress, ISO date for deadline)
  serverId?: number;    // persisted ai_proposals row id (absent for global chat)
  itemId?: string;      // id of the existing item to edit/change (edit_*/state kinds)
  done?: boolean;       // for complete_target / checklist_item / binary create
  // Target creation in a final state:
  targetType?: "binary" | "numeric" | "checklist";
  total?: string;       // numeric goal amount
  current?: string;     // numeric starting progress
  unit?: string;        // numeric unit
  items?: { text: string; done?: boolean; deadline?: string }[]; // checklist items
};

type Mode = "chat" | "grow-start" | "grow-active" | "grow-closing" | "grow-end";

type ProviderInfo = {
  id: string;
  vendor: string;
  context: string;
  connected: boolean;
  keyHint?: string;
  keyPrefix?: string;
  activeModel: string;
  models: string[];
};

const PROVIDERS_DEFAULT: ProviderInfo[] = [
  { id: "ANTHROPIC", vendor: "Anthropic",   context: "200 000 tokens", connected: false, keyPrefix: "sk-ant-", activeModel: "claude-sonnet-4-6",    models: ["claude-sonnet-4-6", "claude-opus-4-8", "claude-haiku-4-5-20251001"] },
  { id: "OPENAI",    vendor: "OpenAI",      context: "128 000 tokens", connected: false, keyPrefix: "sk-",     activeModel: "gpt-4o",               models: ["gpt-4o", "gpt-4o-mini", "o3", "o4-mini"] },
  { id: "MISTRAL",   vendor: "Mistral",     context: "128 000 tokens", connected: false, keyPrefix: "",        activeModel: "mistral-large-latest", models: ["mistral-large-latest", "mistral-small-latest", "codestral-latest", "open-mixtral-8x7b", "open-mistral-7b"] },
  { id: "OLLAMA",    vendor: "Ollama Cloud", context: "cloud", connected: false, keyPrefix: "", activeModel: "gpt-oss:120b", models: ["gpt-oss:120b", "gpt-oss:20b", "qwen3-coder:480b", "deepseek-v3.1:671b"] },
];

const uid = () => Math.random().toString(36).slice(2, 9);

// ── Chat transcript persistence ─────────────────────────────────────────────
// Regular chat survives reloads / closing the panel by caching per-scope
// (one bucket per goal, plus a global bucket) in localStorage. GROW sessions
// are intentionally ephemeral and not persisted.

const CHAT_STORE_PREFIX = "spira:ai-chat:";
const CHAT_MAX_MESSAGES = 100; // cap stored history to keep localStorage small

// The chat provider the user last picked. Persisted so the panel doesn't reset
// to the first stored key (e.g. Anthropic) every time it remounts / reloads.
const ACTIVE_PROVIDER_KEY = "spira:ai-active-provider";

function readSavedProvider(): string | null {
  try {
    return window.localStorage.getItem(ACTIVE_PROVIDER_KEY);
  } catch {
    return null;
  }
}

function saveActiveProvider(id: string) {
  try {
    window.localStorage.setItem(ACTIVE_PROVIDER_KEY, id);
  } catch {
    /* ignore quota / unavailable storage */
  }
}

const chatScopeKey = (goalId?: string) => `${CHAT_STORE_PREFIX}${goalId ?? "global"}`;

function loadTranscript(scopeKey: string): Msg[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(scopeKey);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as Msg[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveTranscript(scopeKey: string, msgs: Msg[]) {
  if (typeof window === "undefined") return;
  try {
    // Persist only settled messages (skip the in-flight streaming placeholder).
    const settled = msgs
      .filter((m) => !m.streaming)
      .slice(-CHAT_MAX_MESSAGES);
    if (settled.length === 0) {
      window.localStorage.removeItem(scopeKey);
    } else {
      window.localStorage.setItem(scopeKey, JSON.stringify(settled));
    }
  } catch {
    /* quota or serialization error — non-fatal */
  }
}

type Suggestion = { id: string; icon: string; text: string };

const SUGGESTIONS_GLOBAL: Suggestion[] = [
  { id: "overview", icon: "🗺️", text: "Help me get a sense of where things stand" },
  { id: "focus",    icon: "🎯", text: "Which goal should I focus on today?" },
  { id: "stuck",    icon: "💭", text: "I'm feeling stuck — where do I begin?" },
];

function buildGoalSuggestions(goal: import("@/lib/spira/types").Goal): Suggestion[] {
  const s: Suggestion[] = [];

  const deadlineDays = goal.deadline
    ? Math.ceil((new Date(goal.deadline).getTime() - Date.now()) / 86_400_000)
    : null;
  const totalTargets = goal.targets.length;
  const doneTargets = goal.targets.filter(
    (t) => (t.type === "binary" && t.done) || !!t.achievedAt,
  ).length;

  if (goal.confidence <= 3)
    s.push({ id: "confidence", icon: "⚡", text: "My confidence is low — help me identify what's blocking me" });

  if (deadlineDays !== null && deadlineDays > 0 && deadlineDays <= 14)
    s.push({ id: "deadline", icon: "⏰", text: `${deadlineDays} day${deadlineDays === 1 ? "" : "s"} left — let's decide what still matters` });

  if (goal.reality.obstacles.length > 0)
    s.push({ id: "obstacles", icon: "🚧", text: `I'm facing ${goal.reality.obstacles.length} obstacle${goal.reality.obstacles.length > 1 ? "s" : ""} — help me think through them` });

  if (doneTargets > 0 && doneTargets < totalTargets)
    s.push({ id: "progress", icon: "📊", text: `${doneTargets}/${totalTargets} targets done — what should I focus on next?` });

  if (totalTargets === 0)
    s.push({ id: "targets", icon: "🎯", text: "Help me define concrete targets for this goal" });

  if (goal.options.length === 0 && s.length < 3)
    s.push({ id: "options", icon: "🔀", text: "What are my strategic options?" });

  if (goal.reality.actions.length === 0 && s.length < 3)
    s.push({ id: "action", icon: "⚡", text: "What's the best next action I can take today?" });

  if (s.length === 0)
    s.push(
      { id: "reflect", icon: "💭", text: "Help me think through where I'm stuck" },
      { id: "reality", icon: "🌱", text: "What's actually true about this goal right now?" },
      { id: "options", icon: "🔀", text: "What are my options from here?" },
    );

  return s.slice(0, 3);
}

/** Builds a Proposal from the `propose_goal_change` tool-call arguments JSON. */
function proposalFromToolArgs(argsJson: string): Proposal | undefined {
  try {
    const data = JSON.parse(argsJson) as Record<string, string> & {
      proposalId?: number;
      items?: { text?: string; done?: boolean; deadline?: string }[];
    };
    const kind = (data.kind || "edit") as Proposal["kind"];
    const value = data.value ?? "";
    const name = data.title ?? "";
    const deadlineVal = data.deadline_value;
    const itemId = data.id != null ? String(data.id) : undefined;
    const done = data.done === "true" ? true : data.done === "false" ? false : undefined;

    // Target-creation extras (binary done / numeric / checklist).
    const total = data.total || undefined;
    const current = data.current || undefined;
    const unit = data.unit || undefined;
    const items = Array.isArray(data.items)
      ? data.items
          .filter((i) => i && typeof i.text === "string" && i.text.trim())
          .map((i) => ({ text: i.text!.trim(), done: i.done === true, deadline: i.deadline }))
      : undefined;
    const targetType: Proposal["targetType"] =
      items && items.length ? "checklist" : total ? "numeric" : "binary";

    let title = value || name;
    let detail: string | undefined;
    let body: string | undefined;

    switch (kind) {
      case "edit":
        title = value;
        detail = data.field === "description" ? "New description" : "New title";
        break;
      case "confidence":
        title = `Confidence → ${value}/10`;
        detail = "Goal confidence";
        break;
      case "deadline":
        title = value; // ISO date
        detail = "Goal deadline";
        break;
      case "target":
      case "task": {
        title = name || value;
        const noun = kind === "task" ? "task" : "target";
        if (targetType === "checklist") {
          const total = items?.length ?? 0;
          const checked = items?.filter((i) => i.done).length ?? 0;
          detail = `New checklist · ${checked}/${total} done`;
        } else if (targetType === "numeric") {
          detail = `New target · ${current ?? 0}/${total}${unit ? " " + unit : ""}`;
        } else {
          detail = done ? `New ${noun} · done` : deadlineVal ? `New ${noun} · due ${deadlineVal}` : `New ${noun}`;
        }
        break;
      }
      case "option":    title = value || name; detail = "Strategy option"; break;
      case "obstacle":  title = value || name; detail = "New obstacle"; break;
      case "action":    title = value || name; detail = "Current action"; break;
      case "note":
        title = name || "Note";
        body = value;
        detail = value.length > 60 ? value.slice(0, 60) + "…" : value;
        break;
      // ── edit existing ──
      case "edit_target":   title = value || name; detail = deadlineVal ? `Edit target · due ${deadlineVal}` : "Edit target"; break;
      case "edit_option":   title = value || name; detail = "Edit option"; break;
      case "edit_obstacle": title = value || name; detail = "Edit obstacle"; break;
      case "edit_action":   title = value || name; detail = "Edit action"; break;
      case "edit_note":
        title = name || "Note";
        body = value;
        detail = "Edit note";
        break;
      // ── state changes ──
      case "complete_target":
        title = done === false ? "Mark target not done" : "Mark target done";
        detail = "Target status";
        break;
      case "target_progress":
        title = `Progress → ${value}`;
        detail = "Target progress";
        break;
      case "select_option":
        title = "Select this option";
        detail = "Strategy option";
        break;
      case "checklist_item":
        title = value || (done === true ? "Check item" : done === false ? "Uncheck item" : "Update item");
        detail = deadlineVal ? `Checklist item · due ${deadlineVal}` : "Checklist item";
        break;
      case "add_checklist_item":
        title = value || name;
        detail = deadlineVal ? `New sub-task · due ${deadlineVal}` : "New sub-task";
        break;
    }

    return {
      id: uid(),
      kind,
      title,
      detail,
      reasoning: data.reasoning,
      status: "pending",
      field: data.field,
      body,
      deadline: deadlineVal,
      rawValue: value || undefined,
      itemId,
      done,
      targetType,
      total,
      current,
      unit,
      items,
      serverId: typeof data.proposalId === "number" ? data.proposalId : undefined,
    };
  } catch {
    return undefined;
  }
}

/**
 * Normalises a date to a full ISO-8601 instant. The backend's deadline columns
 * (goal, target, checklist item) are all `Instant`, so a date-only `YYYY-MM-DD`
 * — which is what the AI and the card's date inputs produce — is rejected on
 * save and silently lost. Full ISO values (from the normal date picker) pass
 * through unchanged. Returns undefined for empty/invalid input.
 */
function normalizeDeadline(value?: string): string | undefined {
  if (!value) return undefined;
  const v = value.trim();
  if (!v) return undefined;
  if (v.includes("T")) return v; // already a full ISO instant
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(v);
  const d = m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : new Date(v);
  return Number.isNaN(d.getTime()) ? undefined : d.toISOString();
}

// ── Panel shell ────────────────────────────────────────────────────────────

const MIN_PANEL_WIDTH = 360;
const MAIN_CONTENT_MIN_WIDTH = 800;
const RESIZE_KEY = "spira:ai-coach-panel-width";

function maxPanelWidth() {
  if (typeof window === "undefined") return 520;
  return Math.max(MIN_PANEL_WIDTH, window.innerWidth - MAIN_CONTENT_MIN_WIDTH);
}
function clampPanelWidth(w: number) {
  return Math.max(MIN_PANEL_WIDTH, Math.min(maxPanelWidth(), w));
}

export function AiPanel() {
  const isOpen = useAi((s) => s.isOpen);
  const close  = useAi((s) => s.close);
  const setWide = useAi((s) => s.setWide);
  const isMobile = useIsMobile();

  const [width, setWidth] = useState<number>(() => {
    if (typeof window === "undefined") return 440;
    const stored = Number(window.localStorage.getItem(RESIZE_KEY));
    return clampPanelWidth(stored || 440);
  });
  const draggingRef = useRef(false);
  const [isDragging, setIsDragging] = useState(false);
  const handleRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onResize = () => setWidth((w) => clampPanelWidth(w));
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  useEffect(() => {
    window.localStorage.setItem(RESIZE_KEY, String(width));
  }, [width]);

  useEffect(() => {
    setWide(isOpen && !isMobile && width >= window.innerWidth / 2);
  }, [isMobile, isOpen, setWide, width]);

  const startDrag = (e: React.PointerEvent) => {
    e.preventDefault();
    draggingRef.current = true;
    setIsDragging(true);
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
    const onMove = (ev: PointerEvent) => {
      if (draggingRef.current) setWidth(clampPanelWidth(ev.clientX));
    };
    const onUp = () => {
      draggingRef.current = false;
      setIsDragging(false);
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
  };

  const Body = <PanelContent onClose={close} />;

  if (isMobile) {
    return (
      <Drawer open={isOpen} onOpenChange={(o) => !o && close()}>
        <DrawerContent className="h-[88vh] flex flex-col px-0 border-0 bg-[#006d67] text-white">
          {/* Title kept for accessibility only — PanelContent renders the
              visible header (wordmark + New chat + close), so avoid duplicating it. */}
          <DrawerHeader className="sr-only">
            <DrawerTitle>spira ai coach</DrawerTitle>
          </DrawerHeader>
          <div className="flex-1 min-h-0 flex flex-col">{Body}</div>
        </DrawerContent>
      </Drawer>
    );
  }

  if (!isOpen) return null;

  return (
    <aside
      className={cn(
        "sticky top-0 z-40 hidden h-screen max-h-screen shrink-0 flex-col border-r border-white/15 bg-[#006d67] text-white shadow-[12px_0_30px_-24px_rgba(0,0,0,0.55)] md:flex",
        isDragging && "[&_iframe]:pointer-events-none",
      )}
      style={{ width: `${width}px` }}
      aria-label="spira ai coach"
    >
      <div
        ref={handleRef}
        onPointerDown={startDrag}
        className="resize-handle ai-panel-left-resize-handle ai-panel-resize-handle"
        role="separator"
        aria-orientation="vertical"
        aria-label="Resize spira ai coach panel"
      />
      {Body}
    </aside>
  );
}

// ── Wordmark ───────────────────────────────────────────────────────────────

function Wordmark() {
  return (
    <>
      <span className="text-[27px] font-extrabold leading-none tracking-[-0.5px]">spira</span>
      <span className="text-[16px] font-normal leading-none text-white/74 pt-0.5">ai coach</span>
    </>
  );
}

// ── Panel content (state machine) ──────────────────────────────────────────

function PanelContent({ onClose }: { onClose: () => void }) {
  const { context } = useAi();
  const goal = useSpira((s) => s.goals.find((g) => g.id === context.goalId));
  const addTarget = useSpira((s) => s.addTarget);
  const updateGoal = useSpira((s) => s.updateGoal);
  const addOption = useSpira((s) => s.addOption);
  const addReality = useSpira((s) => s.addReality);
  const addResource = useSpira((s) => s.addResource);
  const updateTarget = useSpira((s) => s.updateTarget);
  const updateOption = useSpira((s) => s.updateOption);
  const updateReality = useSpira((s) => s.updateReality);
  const updateResource = useSpira((s) => s.updateResource);
  const selectOption = useSpira((s) => s.selectOption);
  const syncError = useSpira((s) => s.syncError);

  // Applying a proposal is optimistic (it shows a success toast immediately),
  // but the store syncs to the backend asynchronously and rolls back on failure
  // (e.g. a value that exceeds a server limit). Surface that failure as an
  // explicit toast so the user isn't left with a "saved" message but no change.
  const lastSyncError = useRef(syncError);
  useEffect(() => {
    if (syncError && syncError !== lastSyncError.current) {
      toast.error(syncError);
    }
    lastSyncError.current = syncError;
  }, [syncError]);

  const applyProposal = useCallback((p: Proposal) => {
    if (!goal) return;
    // Resource titles are labels — the backend rejects > 200 chars (which
    // silently rolled back AI-created notes whose title was long). Clamp to fit.
    const label = (s: string | undefined) => (s ?? "").trim().slice(0, 200) || "Note";
    switch (p.kind) {
      case "edit":
        if (p.field === "title" || p.field === "description") {
          updateGoal(goal.id, { [p.field]: p.title });
          toast.success("Goal updated");
        }
        break;
      case "confidence": {
        const c = parseInt(p.rawValue ?? p.title);
        if (c >= 1 && c <= 10) {
          updateGoal(goal.id, { confidence: c as import("@/lib/spira/types").Confidence });
          toast.success("Confidence updated");
        }
        break;
      }
      case "deadline": {
        const iso = normalizeDeadline(p.rawValue || p.title);
        updateGoal(goal.id, { deadline: iso });
        toast.success("Deadline updated");
        break;
      }
      case "target":
      case "task": {
        const iso = normalizeDeadline(p.deadline);
        if (p.targetType === "checklist" && p.items?.length) {
          const items = p.items.map((it) => ({
            id: "local-" + uid(),
            text: it.text,
            done: !!it.done,
            ...(it.deadline ? { deadline: normalizeDeadline(it.deadline) } : {}),
          }));
          addTarget(goal.id, { type: "checklist", title: p.title, items, ...(iso ? { deadline: iso } : {}) });
          toast.success("Checklist added");
        } else if (p.targetType === "numeric" && p.total) {
          const total = Number(p.total);
          const cur = Number(p.current ?? "0");
          // Create at 0, then set progress via the real id (B-chaining): the
          // backend ties a numeric target's current to start on create.
          addTarget(goal.id, {
            type: "numeric",
            title: p.title,
            total: Number.isNaN(total) ? 0 : total,
            start: 0,
            ...(p.unit ? { unit: p.unit } : {}),
            ...(iso ? { deadline: iso } : {}),
          }).then((created) => {
            if (created && !Number.isNaN(cur) && cur > 0) {
              updateTarget(goal.id, created.id, { current: cur });
            }
          });
          toast.success("Target added");
        } else {
          // Binary. To create an already-done target, create then mark done via
          // the real id (the backend forbids creating a binary target as done).
          addTarget(goal.id, { type: "binary", title: p.title, done: false, ...(iso ? { deadline: iso } : {}) })
            .then((created) => {
              if (created && p.done) updateTarget(goal.id, created.id, { done: true });
            });
          toast.success(p.done ? "Target added & completed" : "Target added");
        }
        break;
      }
      case "option":
        addOption(goal.id, p.title);
        toast.success("Option added");
        break;
      case "obstacle":
        addReality(goal.id, "obstacles", p.title);
        toast.success("Obstacle added");
        break;
      case "action":
        addReality(goal.id, "actions", p.title);
        toast.success("Action added");
        break;
      case "note":
        addResource(goal.id, { type: "note", title: label(p.title), body: p.body ?? "" });
        toast.success("Note saved");
        break;

      // ── edit existing items ──
      case "edit_target": {
        if (!p.itemId) break;
        const iso = normalizeDeadline(p.deadline);
        updateTarget(goal.id, p.itemId, { title: p.title, ...(iso ? { deadline: iso } : {}) });
        toast.success("Target updated");
        break;
      }
      case "edit_option":
        if (p.itemId) { updateOption(goal.id, p.itemId, { text: p.title }); toast.success("Option updated"); }
        break;
      case "edit_obstacle":
        if (p.itemId) { updateReality(goal.id, "obstacles", p.itemId, p.title); toast.success("Obstacle updated"); }
        break;
      case "edit_action":
        if (p.itemId) { updateReality(goal.id, "actions", p.itemId, p.title); toast.success("Action updated"); }
        break;
      case "edit_note":
        if (p.itemId) { updateResource(goal.id, p.itemId, { title: label(p.title), body: p.body ?? "" }); toast.success("Note updated"); }
        break;

      // ── state changes ──
      case "complete_target":
        if (p.itemId) { updateTarget(goal.id, p.itemId, { done: p.done !== false }); toast.success("Target updated"); }
        break;
      case "target_progress": {
        if (!p.itemId) break;
        const n = Number(p.rawValue ?? p.title);
        if (!Number.isNaN(n)) { updateTarget(goal.id, p.itemId, { current: n }); toast.success("Progress updated"); }
        break;
      }
      case "select_option":
        if (p.itemId) { selectOption(goal.id, p.itemId); toast.success("Option selected"); }
        break;
      case "checklist_item": {
        if (!p.itemId) break;
        const parent = goal.targets.find(
          (t) => t.type === "checklist" && t.items.some((i) => i.id === p.itemId),
        );
        if (parent && parent.type === "checklist") {
          const iso = normalizeDeadline(p.deadline);
          const items = parent.items.map((i) =>
            i.id === p.itemId
              ? {
                  ...i,
                  ...(p.rawValue ? { text: p.rawValue } : {}),   // rawValue = real new text (title may be a placeholder)
                  ...(p.done != null ? { done: p.done } : {}),
                  ...(iso ? { deadline: iso } : {}),
                }
              : i,
          );
          updateTarget(goal.id, parent.id, { items });
          toast.success("Checklist updated");
        }
        break;
      }
      case "add_checklist_item": {
        if (!p.itemId) break;
        const parent = goal.targets.find((t) => t.id === p.itemId);
        if (!parent || parent.type !== "checklist") {
          toast.error("Sub-tasks can only be added to a checklist target");
          break;
        }
        const iso = normalizeDeadline(p.deadline);
        const newItem = {
          id: "local-" + uid(),
          text: p.title,
          done: p.done ?? false,
          ...(iso ? { deadline: iso } : {}),
        };
        updateTarget(goal.id, parent.id, { items: [...parent.items, newItem] });
        toast.success("Sub-task added");
        break;
      }
    }
  }, [goal, updateGoal, addTarget, addOption, addReality, addResource,
      updateTarget, updateOption, updateReality, updateResource, selectOption]);

  const scopeKey = chatScopeKey(context.goalId);

  const [mode, setMode] = useState<Mode>("chat");
  const [msgs, setMsgs] = useState<Msg[]>(() => loadTranscript(scopeKey));
  const [gmsgs, setGmsgs] = useState<Msg[]>([]);
  const [busy, setBusy] = useState(false);
  const [session, setSession] = useState<{ total: number; remaining: number; mins: number } | null>(null);
  const [showProvider, setShowProvider] = useState(false);
  const [confirmEnd, setConfirmEnd] = useState(false);
  const [providers, setProviders] = useState<ProviderInfo[]>(PROVIDERS_DEFAULT);
  const [activeProv, setActiveProv] = useState(() => readSavedProvider() || "ANTHROPIC");
  const [tavily, setTavily] = useState<{ connected: boolean; hint?: string }>({ connected: false });

  const stopRef = useRef(false);
  const endedRef = useRef(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  const inGrow = mode === "grow-active" || mode === "grow-closing" || mode === "grow-end";
  const list = inGrow ? gmsgs : msgs;

  // Load saved keys on mount
  useEffect(() => {
    listApiKeys().then((keys: Array<{ provider: string; hint: string; model: string }>) => {
      if (!keys.length) return;
      setProviders((ps) =>
        ps.map((p) => {
          const found = keys.find((k) => k.provider === p.id);
          if (!found) return p;
          return { ...p, connected: true, keyHint: found.hint, activeModel: found.model || p.activeModel };
        }),
      );
      const tav = keys.find((k) => k.provider === "TAVILY");
      if (tav) setTavily({ connected: true, hint: tav.hint });
      // Active chat provider must be an LLM — never Tavily (a search key).
      // Honour the user's last choice if that provider still has a key;
      // otherwise fall back to the first available LLM key.
      const saved = readSavedProvider();
      const savedHasKey = !!saved && saved !== "TAVILY" && keys.some((k) => k.provider === saved);
      if (!savedHasKey) {
        const firstLlm = keys.find((k) => k.provider !== "TAVILY");
        if (firstLlm) {
          setActiveProv(firstLlm.provider);
          saveActiveProvider(firstLlm.provider);
        }
      }
    }).catch(() => {/* backend not running – ignore */});
  }, []);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: 99999, behavior: "smooth" });
  }, [list.length, list[list.length - 1]?.content]);

  // ── transcript persistence ────────────────────────────────────────────────
  // Reload the cached transcript when the scope (goal) changes. The initial
  // mount is already handled by the useState initializer, so skip it.
  const prevScopeRef = useRef(scopeKey);
  useEffect(() => {
    if (prevScopeRef.current === scopeKey) return;
    prevScopeRef.current = scopeKey;
    setMsgs(loadTranscript(scopeKey));
  }, [scopeKey]);

  // Persist regular chat after each settled turn. Skipping while `busy` avoids
  // a localStorage write per streamed token (the streaming placeholder is
  // excluded from storage anyway). Deps intentionally exclude scopeKey: on a
  // scope switch the reload effect sets msgs and this effect then runs with the
  // new scopeKey captured — so we never write one scope's messages into another.
  useEffect(() => {
    if (busy) return;
    saveTranscript(scopeKey, msgs);
  }, [msgs, busy]); // eslint-disable-line react-hooks/exhaustive-deps

  // Restore still-pending proposals from the server for this goal. Surfaces any
  // that aren't already in the (locally cached) transcript — e.g. localStorage
  // was cleared, or the proposal was made on another device.
  useEffect(() => {
    const goalId = context.goalId;
    if (!goalId) return;
    let cancelled = false;
    listGoalProposals(goalId)
      .then((rows) => {
        if (cancelled || rows.length === 0) return;
        setMsgs((prev) => {
          const known = new Set<number>();
          for (const m of prev)
            for (const pr of m.proposals ?? [])
              if (pr.serverId != null) known.add(pr.serverId);

          const restored: Proposal[] = [];
          for (const r of rows) {
            if (known.has(r.id)) continue;
            const p = proposalFromToolArgs(r.payload);
            if (p) restored.push({ ...p, serverId: r.id });
          }

          if (restored.length === 0) return prev;
          return [
            ...prev,
            { id: uid(), role: "assistant" as const, content: "Here are suggestions still waiting for your review:", proposals: restored },
          ];
        });
      })
      .catch(() => {/* backend not running / no proposals – ignore */});
    return () => { cancelled = true; };
  }, [context.goalId]);

  // Clears the visible chat AND its saved transcript for this scope, so the
  // next message is sent with NO history — context comes only from the goal's
  // data. Past mistakes in the conversation stop leaking into the model.
  const newChat = () => {
    if (busy) return;
    setMsgs([]);
    try { window.localStorage.removeItem(scopeKey); } catch { /* ignore */ }
    toast.success("Started a fresh chat — only the goal's data is in context now");
  };

  // ── regular chat ─────────────────────────────────────────────────────────

  const sendChat = (text: string) => {
    if (busy) return;
    setMsgs((p) => [...p, { id: uid(), role: "user", content: text }]);
    setBusy(true);
    stopRef.current = false;

    // Only real conversation turns belong in history — drop system notices,
    // GROW end-cards, and empty placeholders (Anthropic rejects empty content).
    const history: HistoryEntry[] = msgs
      .filter((m) => (m.role === "user" || m.role === "assistant") && m.content.trim() && !m.content.startsWith("⚠️"))
      .map((m) => ({ role: m.role as "user" | "assistant", content: m.content }));

    const id = uid();
    setMsgs((p) => [...p, { id, role: "assistant", content: "", streaming: true }]);
    let accumulated = "";
    const pendingProposals: Proposal[] = [];

    streamChat({
      goalId: context.goalId,
      message: text,
      history,
      provider: activeProv,
      sessionType: "chat",
      onToken: (tok) => {
        if (stopRef.current) return;
        accumulated += tok;
        setMsgs((p) => p.map((m) => (m.id === id ? { ...m, content: accumulated } : m)));
        scrollRef.current?.scrollTo({ top: 99999, behavior: "smooth" });
      },
      onProposal: (argsJson) => {
        const p = proposalFromToolArgs(argsJson);
        if (p) pendingProposals.push(p);
      },
      onDone: () => {
        const content = accumulated.trim() ||
          (pendingProposals.length ? "I've prepared this change for your review." : "");
        setMsgs((p) => p.map((m) =>
          m.id === id
            ? { ...m, streaming: false, content, ...(pendingProposals.length ? { proposals: pendingProposals } : {}) }
            : m,
        ));
        setBusy(false);
      },
      onError: (err) => {
        setBusy(false);
        if (err === "NO_KEY") { setMsgs((p) => p.filter((m) => m.id !== id)); setShowProvider(true); return; }
        const msg = err === "NETWORK" ? "Backend unreachable — is it running?" : (err || "AI error. Try again.");
        // Show the error in place of the empty streaming bubble — visible and
        // persistent (a transient toast is easy to miss for long messages).
        setMsgs((p) => p.map((m) => (m.id === id ? { ...m, streaming: false, content: "⚠️ " + msg } : m)));
        toast.error(msg);
      },
    });
  };

  const stopStream = () => { stopRef.current = true; setBusy(false); };

  // ── GROW ──────────────────────────────────────────────────────────────────

  const startGrow = (mins: number, focus: string) => {
    setGmsgs([]);
    endedRef.current = false;
    const total = mins * 60;
    setSession({ total, remaining: total, mins });
    setMode("grow-active");

    const opening = focus
      ? `I want to work on: ${focus}`
      : "Let's start.";

    const id = uid();
    const history: HistoryEntry[] = focus
      ? [{ role: "user" as const, content: opening }]
      : [];

    if (focus) {
      setGmsgs([{ id: uid(), role: "user", content: opening }]);
    }

    setBusy(true);
    stopRef.current = false;
    let accumulated = "";
    setGmsgs((p) => [...p, { id, role: "assistant", content: "", streaming: true }]);

    streamChat({
      goalId: context.goalId,
      message: opening,
      history: [],
      provider: activeProv,
      sessionType: "grow",
      onToken: (tok) => {
        if (stopRef.current) return;
        accumulated += tok;
        setGmsgs((p) => p.map((m) => (m.id === id ? { ...m, content: accumulated } : m)));
        scrollRef.current?.scrollTo({ top: 99999, behavior: "smooth" });
      },
      onDone: () => {
        setGmsgs((p) => p.map((m) => (m.id === id ? { ...m, streaming: false } : m)));
        setBusy(false);
      },
      onError: (err) => {
        setBusy(false);
        setGmsgs((p) => p.filter((m) => m.id !== id));
        if (err === "NO_KEY") { setShowProvider(true); return; }
        toast.error(err || "AI error.");
      },
    });
  };

  const sendGrow = (text: string) => {
    if (busy) return;
    const userMsg = { id: uid(), role: "user" as const, content: text };
    setGmsgs((p) => [...p, userMsg]);
    setBusy(true);
    stopRef.current = false;

    const history: HistoryEntry[] = gmsgs
      .filter((m) => (m.role === "user" || m.role === "assistant") && m.content.trim() && !m.content.startsWith("⚠️"))
      .map((m) => ({ role: m.role as "user" | "assistant", content: m.content }));

    const id = uid();
    setGmsgs((p) => [...p, { id, role: "assistant", content: "", streaming: true }]);
    let accumulated = "";
    const pendingProposals: Proposal[] = [];

    streamChat({
      goalId: context.goalId,
      message: text,
      history,
      provider: activeProv,
      sessionType: "grow",
      onToken: (tok) => {
        if (stopRef.current) return;
        accumulated += tok;
        setGmsgs((p) => p.map((m) => (m.id === id ? { ...m, content: accumulated } : m)));
        scrollRef.current?.scrollTo({ top: 99999, behavior: "smooth" });
      },
      onProposal: (argsJson) => {
        const p = proposalFromToolArgs(argsJson);
        if (p) pendingProposals.push(p);
      },
      onDone: () => {
        const content = accumulated.trim() ||
          (pendingProposals.length ? "I've prepared this for your review." : "");
        setGmsgs((p) => p.map((m) =>
          m.id === id
            ? { ...m, streaming: false, content, ...(pendingProposals.length ? { proposals: pendingProposals } : {}) }
            : m,
        ));
        setBusy(false);
      },
      onError: (err) => {
        setBusy(false);
        if (err === "NO_KEY") { setGmsgs((p) => p.filter((m) => m.id !== id)); setShowProvider(true); return; }
        const msg = err === "NETWORK" ? "Backend unreachable — is it running?" : (err || "AI error.");
        setGmsgs((p) => p.map((m) => (m.id === id ? { ...m, streaming: false, content: "⚠️ " + msg } : m)));
        toast.error(msg);
      },
    });
  };

  const finishGrow = useCallback(() => {
    if (endedRef.current) return;
    endedRef.current = true;
    setMode("grow-closing");
    setTimeout(() => {
      setGmsgs((p) => [...p, { id: uid(), role: "end", content: "" }]);
      setMode("grow-end");
    }, 800);
  }, []);

  const closeSession = (save: boolean) => {
    setMode("chat");
    setSession(null);
    const note = save
      ? "Session memory saved. Proposals added to the goal for your review."
      : "Session ended without saving memory.";
    setMsgs((p) => [...p, { id: uid(), role: "system", content: note }]);
  };

  // ── GROW timer ────────────────────────────────────────────────────────────

  useEffect(() => {
    if (mode !== "grow-active" && mode !== "grow-closing") return;
    if (!session) return;
    const iv = setInterval(() => {
      setSession((s) => {
        if (!s) return s;
        const remaining = Math.max(0, s.remaining - 1);
        return { ...s, remaining };
      });
    }, 1000);
    return () => clearInterval(iv);
  }, [mode, session?.total]);

  useEffect(() => {
    if (!session) return;
    const frac = 1 - session.remaining / session.total;
    if (frac >= 0.8 && mode === "grow-active") setMode("grow-closing");
    if (session.remaining <= 0 && !endedRef.current) finishGrow();
  }, [session, mode, finishGrow]);

  // ── Provider sheet callbacks ──────────────────────────────────────────────

  const handleSaveKey = async (provId: string, raw: string) => {
    const hint =
      raw.length > 8
        ? `${raw.slice(0, 6)}••••••••${raw.slice(-4)}`
        : `${raw.slice(0, 2)}••••`;
    try {
      await saveApiKey(provId, raw);
      setProviders((ps) =>
        ps.map((p) => (p.id === provId ? { ...p, connected: true, keyHint: hint } : p)),
      );
      setActiveProv(provId);
      saveActiveProvider(provId);
      toast.success(`${provId} key saved`);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed to save key");
    }
  };

  const handleActivateProvider = (id: string) => {
    setActiveProv(id);
    saveActiveProvider(id);
  };

  const handleSaveTavily = async (raw: string) => {
    const hint = raw.length > 8 ? `••••${raw.slice(-4)}` : "••••";
    try {
      await saveApiKey("TAVILY", raw);
      setTavily({ connected: true, hint });
      toast.success("Web search connected");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed to save key");
    }
  };

  const handleModelChange = async (provId: string, model: string) => {
    setProviders((ps) =>
      ps.map((p) => (p.id === provId ? { ...p, activeModel: model } : p)),
    );
    try {
      await updateKeyModel(provId, model);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed to update model");
    }
  };

  // ── Timer display ─────────────────────────────────────────────────────────

  let timerLabel = "";
  let timerFrac = 0;
  const closing = mode === "grow-closing";
  if (session) {
    const rem = Math.ceil(session.remaining);
    const mm = String(Math.floor(rem / 60)).padStart(2, "0");
    const ss = String(rem % 60).padStart(2, "0");
    timerLabel = `${mm}:${ss}`;
    timerFrac = session.remaining / session.total;
  }

  const activeProvider = providers.find((p) => p.id === activeProv) || providers[0];
  const activeLabel = activeProvider.connected ? activeProvider.activeModel : "No key";

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="spira-ai-dark flex flex-col h-full min-h-0 relative">
      {/* Header */}
      <header className="h-[62px] shrink-0 flex items-center justify-between px-5">
        <div className="flex items-baseline gap-[7px]">
          <Wordmark />
        </div>
        <div className="flex items-center gap-2">
          {inGrow ? (
            <>
              <TimerPill frac={timerFrac} closing={closing} label={timerLabel}
                onSkip={() => setSession((s) => s ? { ...s, remaining: Math.min(s.remaining, s.total * 0.18) } : s)} />
              <button
                onClick={() => mode !== "grow-end" && setConfirmEnd(true)}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full border border-white/30 bg-transparent text-white text-xs font-semibold hover:bg-white/10 transition-colors"
              >
                <Ic path={PATHS.x} size={12} /> End
              </button>
            </>
          ) : (
            <>
              {list.length > 0 && (
                <button onClick={newChat} disabled={busy}
                  className="inline-flex items-center gap-1.5 px-2.5 h-[34px] rounded-[9px] text-white/74 text-[12.5px] font-medium hover:bg-white/12 hover:text-white disabled:opacity-40 transition-colors"
                  title="Start a new chat — clears the history so context uses only this goal's data"
                  aria-label="New chat">
                  <Ic path={PATHS.plus} size={13} /> New chat
                </button>
              )}
              <button onClick={onClose}
                className="w-[34px] h-[34px] grid place-items-center rounded-[9px] text-white/74 hover:bg-white/12 hover:text-white transition-colors"
                aria-label="Close">
                <X className="h-4 w-4" />
              </button>
            </>
          )}
        </div>
      </header>

      {/* Context / provider strip */}
      {!inGrow && (
        <div className="flex items-center justify-between gap-2 px-5 pb-3">
          <button
            onClick={() => setShowProvider(true)}
            className="inline-flex items-center gap-[6px] text-[12.5px] font-medium text-white/74 hover:text-white hover:bg-white/10 rounded-lg px-2 py-1 -mx-2 transition-colors"
          >
            <Ic path={PATHS.key} size={12} />
            Bring your own key
            <Ic path={PATHS.chevron} size={12} className="opacity-60" />
          </button>
          <span className="inline-flex items-center gap-[6px] text-[12px] font-medium text-white shrink-0 font-mono">
            <span className={cn(
              "w-[7px] h-[7px] rounded-full",
              activeProvider.connected
                ? "bg-[#5fd0a8] shadow-[0_0_0_3px_rgba(95,208,168,0.2)]"
                : "bg-[#d99a4e] shadow-[0_0_0_3px_rgba(217,154,78,0.2)]"
            )} />
            {activeLabel}
          </span>
        </div>
      )}

      {/* Closing banner */}
      {closing && (
        <div className="mx-4 mb-2.5 px-3 py-2.5 rounded-[10px] bg-white/10 flex items-center gap-2 text-[12.5px] text-white">
          <Ic path={PATHS.leaf} size={12} />
          The session is gently moving toward a close
        </div>
      )}

      {/* Body */}
      <div ref={scrollRef} data-vaul-no-drag className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden px-4 py-2 flex flex-col gap-4 scrollbar-thin scrollbar-thumb-white/20">

        {/* Empty state */}
        {!inGrow && msgs.length === 0 && (
          <div className="pt-5 pb-2 text-center">
            <div className="w-[52px] h-[52px] rounded-full bg-white/10 border border-white/20 grid place-items-center mx-auto mb-4">
              <Ic path={PATHS.leaf} size={20} />
            </div>
            <p className="text-[14px] leading-[1.6] text-white/74 max-w-[30ch] mx-auto mb-5">
              {goal
                ? `I'm here to help with "${goal.title}". Ask anything or start a GROW session.`
                : "I'm here to help you think. Ask anything or open a goal for a focused session."}
            </p>
            <div className="flex flex-col gap-2">
              {(goal ? buildGoalSuggestions(goal) : SUGGESTIONS_GLOBAL).map((s) => (
                <button key={s.id}
                  onClick={() => sendChat(s.text)}
                  className="flex items-center gap-2.5 px-3.5 py-3 rounded-xl bg-white/10 border border-white/20 text-white text-[13.5px] text-left hover:border-white/35 hover:-translate-y-px transition-all">
                  <span className="text-base">{s.icon}</span>
                  {s.text}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Messages */}
        {list.map((m) => {
          if (m.role === "user") {
            return (
              <div key={m.id} className="group flex flex-col items-end gap-1">
                <div className="max-w-[86%] min-w-0 px-3.5 py-2.5 rounded-2xl rounded-br-sm bg-white text-[#083f3a] text-[14px] leading-[1.5] whitespace-pre-wrap break-words [overflow-wrap:anywhere] select-text">
                  {m.content}
                </div>
                <CopyButton text={m.content} />
              </div>
            );
          }
          if (m.role === "system") {
            return (
              <div key={m.id} className="flex items-center gap-2 self-center text-[12.5px] text-white/74 bg-white/10 px-3 py-1.5 rounded-full">
                <Ic path={PATHS.check} size={12} /> {m.content}
              </div>
            );
          }
          if (m.role === "end") {
            return (
              <GrowEndCard key={m.id} proposals={0}
                onSave={() => closeSession(true)}
                onDiscard={() => closeSession(false)} />
            );
          }
          // assistant
          return (
            <div key={m.id} className="group flex flex-col gap-2.5">
              <div className="text-[14.5px] leading-[1.62] text-white max-w-[94%] min-w-0 break-words [overflow-wrap:anywhere] select-text">
                <Markdown text={m.content} />
                {m.streaming && <span className="inline-block w-[7px] h-[15px] ml-0.5 align-text-bottom bg-white rounded-sm animate-pulse" />}
              </div>
              {!m.streaming && m.content && <CopyButton text={m.content} />}
              {!m.streaming && m.proposals?.map((p) => (
                <ProposalCard key={p.id} p={p}
                  onResolve={(status) => {
                    (inGrow ? setGmsgs : setMsgs)((msgs) => msgs.map((msg) =>
                      msg.id === m.id
                        ? { ...msg, proposals: msg.proposals?.map((pr) => pr.id === p.id ? { ...pr, status } : pr) }
                        : msg,
                    ));
                    // Record the decision server-side (best-effort) so it survives reload.
                    if (p.serverId != null) {
                      (status === "approved" ? approveProposal : rejectProposal)(p.serverId).catch(() => {});
                    }
                  }}
                  onApprove={applyProposal}
                  onInstruct={(instruction) => {
                    // The user wants the AI to revise this proposal. Mark the current
                    // one superseded (dismissed), then send their instruction back so
                    // the AI re-proposes.
                    (inGrow ? setGmsgs : setMsgs)((msgs) => msgs.map((msg) =>
                      msg.id === m.id
                        ? { ...msg, proposals: msg.proposals?.map((pr) => pr.id === p.id ? { ...pr, status: "rejected" as const } : pr) }
                        : msg,
                    ));
                    if (p.serverId != null) rejectProposal(p.serverId).catch(() => {});
                    const label = (KIND_META[p.kind]?.label ?? "change").toLowerCase();
                    const ctx = p.detail ? `${p.title} (${p.detail})` : p.title;
                    (inGrow ? sendGrow : sendChat)(
                      `Revise your proposed ${label} "${ctx}": ${instruction}. Re-propose it with the change applied.`,
                    );
                  }}
                />
              ))}
            </div>
          );
        })}
      </div>

      {/* Footer */}
      {mode !== "grow-end" && (
        <>
          {!inGrow && goal && (
            <div className="px-4 pb-1">
              <button
                onClick={() => setMode("grow-start")}
                className="inline-flex items-center gap-[7px] px-3 py-1.5 rounded-full border border-dashed border-white/30 text-white/74 text-[12.5px] font-medium hover:text-white hover:border-white transition-colors"
              >
                <Ic path={PATHS.leaf} size={14} /> Start a GROW session
              </button>
            </div>
          )}
          {inGrow && (
            <div className="px-4 pb-1">
              <button onClick={() => setConfirmEnd(true)}
                className="text-white/60 text-[12.5px] underline underline-offset-2 hover:text-white transition-colors">
                End session early
              </button>
            </div>
          )}
          <Composer
            onSend={inGrow ? sendGrow : sendChat}
            placeholder={inGrow ? "Answer in your own words…" : "Ask, plan, or request an action…"}
            busy={busy}
            onStop={stopStream}
          />
        </>
      )}

      {/* Overlays */}
      {mode === "grow-start" && (
        <GrowStartOverlay onStart={startGrow} onCancel={() => setMode("chat")} />
      )}
      {showProvider && (
        <ProviderSheet
          providers={providers}
          activeId={activeProv}
          onActivate={handleActivateProvider}
          onSaveKey={handleSaveKey}
          onModelChange={handleModelChange}
          tavily={tavily}
          onSaveTavily={handleSaveTavily}
          onClose={() => setShowProvider(false)}
        />
      )}
      {confirmEnd && (
        <EndConfirmDialog
          remainingLabel={timerLabel}
          onConfirm={() => { setConfirmEnd(false); finishGrow(); }}
          onCancel={() => setConfirmEnd(false)}
        />
      )}
    </div>
  );
}

// ── Icon system ────────────────────────────────────────────────────────────

const PATHS = {
  leaf:    '<path d="M7 20h10"/><path d="M10 20c5.5-2.5.8-6.4 3-10"/><path d="M9.5 9.4c1.1.8 1.8 2.2 2.3 3.7-2 .4-3.5.4-4.8-.3-1.2-.6-2.3-1.9-3-4.2 2.8-.5 4.4 0 5.5.8z"/><path d="M14.1 6a7 7 0 0 0-1.1 4c1.9-.1 3.3-.6 4.3-1.4 1-1 1.6-2.3 1.7-4.6-2.7.1-4 1-4.9 2z"/>',
  key:     '<path d="m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L21 5"/><path d="m21 2-9.6 9.6"/><circle cx="7.5" cy="15.5" r="5.5"/>',
  chevron: '<path d="m6 9 6 6 6-6"/>',
  clock:   '<path d="M12 6v6l4 2"/><circle cx="12" cy="12" r="10"/>',
  check:   '<path d="M20 6 9 17l-5-5"/>',
  x:       '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
  sparkles:'<path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .962 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.962 0z"/>',
  brain:   '<path d="M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z"/><path d="M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z"/>',
  shield:  '<path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/>',
  plus:    '<path d="M5 12h14"/><path d="M12 5v14"/>',
  switch_: '<path d="M8 3 4 7l4 4"/><path d="M4 7h16"/><path d="m16 21 4-4-4-4"/><path d="M20 17H4"/>',
  pencil:  '<path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"/>',
  target:  '<circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/>',
  copy:    '<rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/>',
};

function Ic({ path, size, className }: { path: string; size: number; className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
      style={{ width: size + 1, height: size + 1 }}
      className={className}
      aria-hidden
      dangerouslySetInnerHTML={{ __html: path }}
    />
  );
}

// ── Markdown ───────────────────────────────────────────────────────────────

function Markdown({ text }: { text: string }) {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={{
        p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
        strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
        em: ({ children }) => <em className="italic">{children}</em>,
        ul: ({ children }) => <ul className="list-disc pl-5 mb-2 space-y-0.5">{children}</ul>,
        ol: ({ children }) => <ol className="list-decimal pl-5 mb-2 space-y-0.5">{children}</ol>,
        li: ({ children }) => <li>{children}</li>,
        h1: ({ children }) => <div className="font-['Playfair_Display'] text-[19px] font-semibold mb-1.5 mt-3">{children}</div>,
        h2: ({ children }) => <div className="font-['Playfair_Display'] text-[16.5px] font-semibold mb-1.5 mt-3">{children}</div>,
        h3: ({ children }) => <div className="font-['Playfair_Display'] text-[15px] font-semibold mb-1.5 mt-2">{children}</div>,
        h4: ({ children }) => <div className="font-['Playfair_Display'] text-[14px] font-semibold mb-1 mt-2">{children}</div>,
        code: ({ children, className }) => {
          const isBlock = className?.includes("language-");
          return isBlock
            ? <pre className="bg-white/10 rounded-lg px-3 py-2 overflow-x-auto text-[13px] font-mono mb-2"><code>{children}</code></pre>
            : <code className="bg-white/15 rounded px-1 py-0.5 text-[13px] font-mono">{children}</code>;
        },
        blockquote: ({ children }) => <blockquote className="border-l-2 border-white/40 pl-3 my-2 text-white/80">{children}</blockquote>,
        a: ({ href, children }) => <a href={href} target="_blank" rel="noopener noreferrer" className="underline underline-offset-2 text-white/90 hover:text-white">{children}</a>,
        hr: () => <hr className="border-white/20 my-3" />,
      }}
    >
      {text}
    </ReactMarkdown>
  );
}

// ── Timer pill ─────────────────────────────────────────────────────────────

function TimerPill({ frac, closing, label, onSkip }: {
  frac: number; closing: boolean; label: string; onSkip: () => void;
}) {
  return (
    <button onClick={onSkip} title="Skip (demo)"
      className={cn(
        "inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/12 border-none text-white font-sans",
        closing && "text-[#f0b860]",
      )}>
      <Ic path={PATHS.clock} size={12} />
      <span className={cn("text-[12.5px] font-semibold tabular-nums tracking-[0.02em]", closing && "text-[#f0b860]")}>
        {label}
      </span>
      <span className="w-[46px] h-1 rounded-full bg-white/26 overflow-hidden">
        <span
          className="block h-full rounded-full transition-[width] duration-[900ms] linear"
          style={{
            width: `${frac * 100}%`,
            background: closing ? "#f0b860" : "white",
          }}
        />
      </span>
    </button>
  );
}

// ── Proposal card ──────────────────────────────────────────────────────────

const KIND_META: Record<string, { icon: string; label: string }> = {
  target:          { icon: PATHS.target,   label: "New target" },
  task:            { icon: PATHS.check,    label: "New task" },
  option:          { icon: PATHS.sparkles, label: "Strategy option" },
  note:            { icon: PATHS.pencil,   label: "Resource note" },
  edit:            { icon: PATHS.pencil,   label: "Goal edit" },
  obstacle:        { icon: PATHS.shield,   label: "New obstacle" },
  action:          { icon: PATHS.leaf,     label: "Current action" },
  confidence:      { icon: PATHS.brain,    label: "Confidence" },
  deadline:        { icon: PATHS.clock,    label: "Deadline" },
  edit_target:     { icon: PATHS.target,   label: "Edit target" },
  edit_option:     { icon: PATHS.sparkles, label: "Edit option" },
  edit_obstacle:   { icon: PATHS.shield,   label: "Edit obstacle" },
  edit_action:     { icon: PATHS.leaf,     label: "Edit action" },
  edit_note:       { icon: PATHS.pencil,   label: "Edit note" },
  complete_target: { icon: PATHS.check,    label: "Target status" },
  target_progress: { icon: PATHS.target,   label: "Target progress" },
  select_option:   { icon: PATHS.sparkles, label: "Select option" },
  checklist_item:  { icon: PATHS.check,    label: "Checklist item" },
  add_checklist_item: { icon: PATHS.plus,  label: "New sub-task" },
};

const PROPOSAL_INPUT_CLS =
  "w-full border border-[#d9dddc] rounded-lg px-3 py-2 text-[14px] text-[#083f3a] bg-white outline-none focus:border-[#006d67] focus:ring-2 focus:ring-[#006d67]/12 transition";

function ProposalCard({ p, onResolve, onApprove, onInstruct }: {
  p: Proposal;
  onResolve: (status: "approved" | "rejected") => void;
  onApprove: (p: Proposal) => void;
  /** User wants the AI to revise this proposal — sends their instruction back to the AI. */
  onInstruct: (instruction: string) => void;
}) {
  const [whyOpen, setWhyOpen] = useState(false);
  const [instructing, setInstructing] = useState(false);
  const [instruction, setInstruction] = useState("");
  const meta = KIND_META[p.kind] || KIND_META.target;
  const settled = p.status !== "pending";

  const sendInstruction = () => {
    const t = instruction.trim();
    if (!t) return;
    onInstruct(t);
    setInstruction("");
    setInstructing(false);
  };

  return (
    <div className="rounded-[14px] border border-white/20 bg-white text-[#083f3a] p-4 shadow-[0_6px_20px_-14px_rgba(0,0,0,0.4)] max-w-full">
      <div className="mb-2">
        <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#006d67]">
          <Ic path={meta.icon} size={12} className="text-[#006d67]" />
          {meta.label}
        </span>
      </div>

      {instructing ? (
        <div className="flex flex-col gap-2">
          <div className="font-['Playfair_Display'] text-[15px] font-semibold leading-[1.25]">{p.title}</div>
          <p className="text-[12px] text-[#083f3a]/55">Tell the AI how to change this — it will re-propose.</p>
          <textarea
            value={instruction}
            onChange={(e) => setInstruction(e.target.value)}
            rows={2}
            autoFocus
            placeholder="e.g. “in English”, “make it shorter”, “due next Friday”"
            className={cn(PROPOSAL_INPUT_CLS, "resize-none")}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sendInstruction(); }
            }}
          />
          <div className="mt-1 flex items-center gap-2">
            <button onClick={sendInstruction} disabled={!instruction.trim()}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#006d67] text-white text-[13px] font-semibold disabled:opacity-40 hover:bg-[#005b56] transition-colors">
              <Ic path={PATHS.sparkles} size={14} /> Send to AI
            </button>
            <button onClick={() => setInstructing(false)}
              className="ml-auto text-[13px] text-[#083f3a]/50 hover:text-[#083f3a] transition-colors px-1.5 py-2">
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <>
          <div className="font-['Playfair_Display'] text-[17px] font-semibold leading-[1.25]">{p.title}</div>
          {p.detail && <p className="mt-1.5 text-[13.5px] leading-[1.5] text-[#083f3a]/60">{p.detail}</p>}
          {p.reasoning && (
            <button onClick={() => setWhyOpen((o) => !o)}
              className="inline-flex items-center gap-1.5 mt-2.5 text-[12.5px] text-[#083f3a]/60 hover:text-[#083f3a] transition-colors">
              <Ic path={PATHS.chevron} size={12}
                className={cn("transition-transform duration-150", whyOpen && "rotate-180")} />
              Why this
            </button>
          )}
          {whyOpen && <p className="mt-1.5 text-[12.5px] leading-[1.55] text-[#083f3a]/60">{p.reasoning}</p>}

          {settled ? (
            <div className={cn(
              "mt-3 inline-flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full font-medium",
              p.status === "approved" ? "bg-[#006d67]/10 text-[#006d67]" : "bg-black/6 text-[#083f3a]/50",
            )}>
              <Ic path={p.status === "approved" ? PATHS.check : PATHS.x} size={12} />
              {p.status === "approved" ? "Added to goal" : "Dismissed"}
            </div>
          ) : (
            <div className="mt-3.5 flex items-center gap-2">
              <button onClick={() => { onResolve("approved"); onApprove(p); }}
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#006d67] text-white text-[13px] font-semibold hover:bg-[#005b56] transition-colors">
                <Ic path={PATHS.check} size={14} /> Accept
              </button>
              <button onClick={() => setInstructing(true)}
                title="Ask the AI to change this proposal"
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-[9px] border border-[#d9dddc] text-[#083f3a] text-[13px] font-medium hover:border-[#006d67]/40 transition-colors">
                <Ic path={PATHS.pencil} size={13} /> Edit
              </button>
              <button
                onClick={() => onResolve("rejected")}
                className="ml-auto text-[13px] text-[#083f3a]/50 hover:text-red-600 transition-colors px-1.5 py-2">
                Dismiss
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

// ── GROW start overlay ─────────────────────────────────────────────────────

function GrowStartOverlay({ onStart, onCancel }: {
  onStart: (mins: number, focus: string) => void;
  onCancel: () => void;
}) {
  const [mins, setMins] = useState(30);
  const [focus, setFocus] = useState("");

  return (
    <div className="absolute inset-0 z-40 flex items-end bg-[rgba(8,40,38,0.4)] backdrop-blur-[2px]">
      <div className="w-full bg-white text-[#083f3a] rounded-t-[22px] px-5 pt-6 pb-5"
        style={{ animation: "slideUp 0.3s cubic-bezier(0.2,0.8,0.2,1) both" }}>
        <span className="inline-flex items-center gap-1.5 text-[11px] uppercase tracking-[0.07em] font-bold text-[#006d67]">
          <Ic path={PATHS.leaf} size={14} className="text-[#006d67]" /> GROW session
        </span>
        <h3 className="font-['Playfair_Display'] text-[22px] font-semibold mt-2.5 mb-1 leading-[1.18]">
          Focused time on a single goal
        </h3>
        <p className="text-[13.5px] text-[#083f3a]/60 mb-4 leading-[1.5]">
          A conversation without rush. I'll help you get clarity — the decisions stay yours.
        </p>
        <div className="grid grid-cols-4 gap-2 mb-3.5">
          {([15, 30, 45, 60] as const).map((m) => (
            <button key={m} onClick={() => setMins(m)}
              className={cn(
                "flex flex-col items-center py-2.5 rounded-xl border text-[#083f3a] transition-colors",
                mins === m
                  ? "border-[#006d67] bg-[#e7f3f1] text-[#006d67]"
                  : "border-[#e6e4df] hover:border-[#006d67]/40",
              )}>
              <span className="text-[18px] font-bold leading-none">{m}</span>
              <span className="text-[11px] text-current/60 mt-0.5">min</span>
            </button>
          ))}
        </div>
        <input
          value={focus}
          onChange={(e) => setFocus(e.target.value)}
          placeholder="What do you want to work on? (optional)"
          className="w-full px-3.5 py-3 border border-[#e6e4df] rounded-xl text-[14px] bg-white text-[#083f3a] mb-3.5 outline-none focus:border-[#006d67] focus:ring-2 focus:ring-[#006d67]/12 transition"
        />
        <button onClick={() => onStart(mins, focus)}
          className="w-full flex items-center justify-center gap-2 py-3.5 rounded-xl bg-[#006d67] text-white text-[14.5px] font-semibold hover:bg-[#005b56] transition-colors">
          Start session · {mins} min
        </button>
        <button onClick={onCancel}
          className="block mx-auto mt-2 text-[13px] text-[#083f3a]/50 hover:text-[#083f3a] transition-colors py-1.5">
          Cancel
        </button>
      </div>
      <style>{`@keyframes slideUp { from { transform: translateY(34px); opacity: 0.25; } to { transform: translateY(0); opacity: 1; } }`}</style>
    </div>
  );
}

// ── GROW end card ──────────────────────────────────────────────────────────

function GrowEndCard({ proposals, onSave, onDiscard }: {
  proposals: number; onSave: () => void; onDiscard: () => void;
}) {
  return (
    <div className="rounded-[14px] border border-white/20 bg-white text-[#083f3a] p-4 shadow-[0_6px_20px_-14px_rgba(0,0,0,0.4)]">
      <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#006d67]">
        <Ic path={PATHS.brain} size={12} className="text-[#006d67]" /> Session wrap-up
      </span>
      <p className="mt-2 text-[13.5px] leading-[1.5] text-[#083f3a]/60">
        Save what I learned about this goal? Next time we'll continue instead of starting from scratch.
      </p>
      {proposals > 0 && (
        <div className="mt-2.5 flex items-center gap-2 px-3 py-2 rounded-[9px] bg-[#e7f3f1] text-[#006d67] text-[12.5px]">
          <Ic path={PATHS.target} size={12} /> {proposals} proposal waiting in the goal
        </div>
      )}
      <div className="mt-3.5 flex items-center gap-2">
        <button onClick={onSave}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#006d67] text-white text-[13px] font-semibold hover:bg-[#005b56] transition-colors">
          <Ic path={PATHS.check} size={14} /> Save memory
        </button>
        <button onClick={onDiscard}
          className="ml-auto text-[13px] text-[#083f3a]/50 hover:text-[#083f3a] transition-colors px-1.5">
          Don't save
        </button>
      </div>
    </div>
  );
}

// ── Provider sheet ─────────────────────────────────────────────────────────

function ProviderSheet({ providers, activeId, onActivate, onSaveKey, onModelChange, tavily, onSaveTavily, onClose }: {
  providers: ProviderInfo[];
  activeId: string;
  onActivate: (id: string) => void;
  onSaveKey: (id: string, key: string) => void;
  onModelChange: (id: string, model: string) => void;
  tavily: { connected: boolean; hint?: string };
  onSaveTavily: (key: string) => void;
  onClose: () => void;
}) {
  const [editing, setEditing] = useState<string | null>(null);
  const [keyVal, setKeyVal] = useState("");
  const [showKey, setShowKey] = useState(false);
  const [tavilyVal, setTavilyVal] = useState("");
  const [editingTavily, setEditingTavily] = useState(false);
  const [modelLists, setModelLists] = useState<Record<string, string[]>>({});
  const [loadingModels, setLoadingModels] = useState<string | null>(null);
  const [openDropdown, setOpenDropdown] = useState<string | null>(null);

  const loadModels = async (provId: string) => {
    if (modelLists[provId] || loadingModels === provId) return;
    setLoadingModels(provId);
    try {
      const list = await fetchProviderModels(provId);
      setModelLists((m) => ({ ...m, [provId]: list }));
    } catch {
      /* silently fall back to static list */
    } finally {
      setLoadingModels(null);
    }
  };

  const handleDropdownToggle = (provId: string, connected: boolean) => {
    if (!connected) return;
    if (openDropdown === provId) {
      setOpenDropdown(null);
    } else {
      setOpenDropdown(provId);
      loadModels(provId);
    }
  };

  return (
    <div className="absolute inset-0 z-45 flex items-end bg-[rgba(8,40,38,0.42)] backdrop-blur-[2px]"
      onClick={onClose}>
      <div className="w-full max-h-[88%] overflow-y-auto bg-white text-[#083f3a] rounded-t-[22px] px-5 pt-3 pb-5"
        onClick={(e) => e.stopPropagation()}
        style={{ animation: "slideUp 0.3s cubic-bezier(0.2,0.8,0.2,1) both" }}>
        <div className="w-[38px] h-1 rounded-full bg-[#e6e4df] mx-auto mb-3.5" />
        <div className="flex items-start justify-between gap-2 mb-1">
          <div>
            <span className="inline-flex items-center gap-1.5 text-[11px] uppercase tracking-[0.07em] font-bold text-[#006d67]">
              <Ic path={PATHS.key} size={14} className="text-[#006d67]" /> Bring your own key
            </span>
            <h3 className="font-['Playfair_Display'] text-[22px] font-semibold mt-1.5 leading-[1.18]">AI providers</h3>
          </div>
          <button onClick={onClose}
            className="w-[34px] h-[34px] grid place-items-center rounded-[9px] text-[#083f3a]/50 hover:bg-black/5 hover:text-[#083f3a] transition-colors">
            <Ic path={PATHS.x} size={16} />
          </button>
        </div>
        <p className="text-[13.5px] text-[#083f3a]/60 mb-4 leading-[1.5]">
          Keys are stored encrypted on your account. Keep several connected and switch anytime.
        </p>

        <div className="flex flex-col gap-3">
          {providers.map((p) => {
            const isActive = p.id === activeId && p.connected;
            const dropOpen = openDropdown === p.id;
            const fetchedModels = modelLists[p.id];
            const isLoadingMdl = loadingModels === p.id;

            return (
              <div key={p.id}
                className={cn(
                  "border rounded-xl p-3.5",
                  isActive ? "border-[#006d67] bg-[#e7f3f1]/50" : "border-[#e6e4df]",
                )}>
                {/* Header row */}
                <div className="flex items-center justify-between gap-2">
                  <div>
                    <span className="font-semibold text-[15px]">{p.vendor}</span>
                    <span className="ml-2 text-[12px] text-[#083f3a]/50">{p.context}</span>
                  </div>
                  {isActive ? (
                    <span className="inline-flex items-center gap-1 text-[12px] font-semibold text-[#006d67] bg-[#006d67]/10 px-2 py-0.5 rounded-full">
                      <Ic path={PATHS.check} size={11} /> Active
                    </span>
                  ) : p.connected ? (
                    <button onClick={() => onActivate(p.id)}
                      className="inline-flex items-center gap-1 text-[12px] font-medium text-[#083f3a] border border-[#e6e4df] px-2.5 py-1 rounded-lg hover:border-[#006d67]/40 transition-colors">
                      <Ic path={PATHS.switch_} size={12} /> Use this
                    </button>
                  ) : (
                    <span className="text-[12px] text-[#083f3a]/40">Not connected</span>
                  )}
                </div>

                {/* Model selector — only when connected */}
                {p.connected && editing !== p.id && (
                  <div className="relative mt-2.5">
                    <button
                      onClick={() => handleDropdownToggle(p.id, p.connected)}
                      className="w-full flex items-center justify-between gap-2 px-3 py-2 rounded-lg border border-[#e6e4df] bg-white hover:border-[#006d67]/40 transition-colors text-left"
                    >
                      <span className="text-[13px] text-[#083f3a] font-mono truncate">
                        {p.activeModel || "Select model"}
                      </span>
                      <Ic path={PATHS.chevron} size={14}
                        className={cn("shrink-0 text-[#083f3a]/50 transition-transform duration-150", dropOpen && "rotate-180")} />
                    </button>

                    {dropOpen && (
                      <div className="absolute top-full left-0 right-0 z-50 mt-1 bg-white border border-[#e6e4df] rounded-xl shadow-lg overflow-hidden">
                        {isLoadingMdl ? (
                          <div className="px-3 py-3 text-[13px] text-[#083f3a]/50 text-center">
                            Loading models…
                          </div>
                        ) : (fetchedModels ?? p.models).length === 0 ? (
                          <div className="px-3 py-3 text-[13px] text-[#083f3a]/50 text-center">No models found</div>
                        ) : (
                          <div className="max-h-[200px] overflow-y-auto">
                            {(fetchedModels ?? p.models).map((m) => (
                              <button key={m}
                                onClick={() => { onModelChange(p.id, m); setOpenDropdown(null); }}
                                className={cn(
                                  "w-full text-left px-3 py-2.5 text-[13px] font-mono transition-colors",
                                  m === p.activeModel
                                    ? "bg-[#e7f3f1] text-[#006d67] font-semibold"
                                    : "text-[#083f3a] hover:bg-[#f4f5f5]",
                                )}>
                                {m}
                              </button>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}

                {/* Key hint + replace */}
                {p.connected && editing !== p.id && (
                  <div className="flex items-center justify-between mt-2.5">
                    <span className="inline-flex items-center gap-1.5 text-[12px] font-mono text-[#083f3a]/50">
                      <Ic path={PATHS.shield} size={12} /> {p.keyHint}
                    </span>
                    <button onClick={() => { setEditing(p.id); setKeyVal(""); setShowKey(false); setOpenDropdown(null); }}
                      className="text-[12px] text-[#006d67] hover:underline">Replace key</button>
                  </div>
                )}

                {/* Connect key button */}
                {!p.connected && editing !== p.id && (
                  <button onClick={() => { setEditing(p.id); setKeyVal(""); setShowKey(false); }}
                    className="mt-2.5 inline-flex items-center gap-1.5 text-[13px] text-[#006d67] hover:text-[#005b56] font-medium">
                    <Ic path={PATHS.plus} size={14} /> Connect a key
                  </button>
                )}

                {/* Key input form */}
                {editing === p.id && (
                  <div className="mt-3">
                    <div className="flex items-center gap-2 px-3 py-1 border-2 border-[#006d67] rounded-xl bg-white shadow-[0_0_0_3px_rgba(0,109,103,0.12)]">
                      <input
                        type={showKey ? "text" : "password"}
                        value={keyVal}
                        onChange={(e) => setKeyVal(e.target.value)}
                        placeholder={p.keyPrefix ? `${p.keyPrefix}…` : "API key"}
                        autoFocus
                        className="flex-1 border-none outline-none font-mono text-[13.5px] text-[#083f3a] bg-transparent py-1.5 tracking-[0.02em]"
                      />
                      <button onClick={() => setShowKey((s) => !s)}
                        className="bg-black/5 text-[#083f3a]/60 text-[11.5px] font-semibold px-2.5 py-1.5 rounded-lg">
                        {showKey ? "Hide" : "Show"}
                      </button>
                    </div>
                    <div className="flex items-center gap-2 mt-2.5">
                      <button
                        disabled={!keyVal.trim()}
                        onClick={() => { onSaveKey(p.id, keyVal.trim()); setEditing(null); }}
                        className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#006d67] text-white text-[13px] font-semibold hover:bg-[#005b56] disabled:opacity-40 transition-colors">
                        <Ic path={PATHS.check} size={14} /> Save &amp; activate
                      </button>
                      <button onClick={() => setEditing(null)}
                        className="text-[13px] text-[#083f3a]/50 hover:text-[#083f3a] px-2 transition-colors">Cancel</button>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Web search (Tavily) — a search key, separate from chat providers */}
        <div className="mt-5 pt-4 border-t border-[#e6e4df]">
          <span className="inline-flex items-center gap-1.5 text-[11px] uppercase tracking-[0.07em] font-bold text-[#006d67]">
            <Ic path={PATHS.key} size={13} className="text-[#006d67]" /> Web search
          </span>
          <p className="text-[12.5px] text-[#083f3a]/60 mt-1 mb-2.5 leading-[1.5]">
            Add a Tavily key (tavily.com) to let the assistant search the web. Optional.
          </p>
          <div className="border rounded-xl p-3.5 border-[#e6e4df]">
            <div className="flex items-center justify-between gap-2">
              <span className="font-semibold text-[15px]">Tavily</span>
              {tavily.connected
                ? <span className="inline-flex items-center gap-1 text-[12px] font-semibold text-[#006d67] bg-[#006d67]/10 px-2 py-0.5 rounded-full"><Ic path={PATHS.check} size={11} /> Connected</span>
                : <span className="text-[12px] text-[#083f3a]/40">Not connected</span>}
            </div>

            {tavily.connected && !editingTavily && (
              <div className="flex items-center justify-between mt-2.5">
                <span className="inline-flex items-center gap-1.5 text-[12px] font-mono text-[#083f3a]/50">
                  <Ic path={PATHS.shield} size={12} /> {tavily.hint}
                </span>
                <button onClick={() => { setEditingTavily(true); setTavilyVal(""); }}
                  className="text-[12px] text-[#006d67] hover:underline">Replace key</button>
              </div>
            )}

            {!tavily.connected && !editingTavily && (
              <button onClick={() => { setEditingTavily(true); setTavilyVal(""); }}
                className="mt-2.5 inline-flex items-center gap-1.5 text-[13px] text-[#006d67] hover:text-[#005b56] font-medium">
                <Ic path={PATHS.plus} size={14} /> Connect a key
              </button>
            )}

            {editingTavily && (
              <div className="mt-3">
                <div className="flex items-center gap-2 px-3 py-1 border-2 border-[#006d67] rounded-xl bg-white shadow-[0_0_0_3px_rgba(0,109,103,0.12)]">
                  <input
                    type="password"
                    value={tavilyVal}
                    onChange={(e) => setTavilyVal(e.target.value)}
                    placeholder="tvly-…"
                    autoFocus
                    className="flex-1 border-none outline-none font-mono text-[13.5px] text-[#083f3a] bg-transparent py-1.5 tracking-[0.02em]"
                  />
                </div>
                <div className="flex items-center gap-2 mt-2.5">
                  <button
                    disabled={!tavilyVal.trim()}
                    onClick={() => { onSaveTavily(tavilyVal.trim()); setEditingTavily(false); }}
                    className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#006d67] text-white text-[13px] font-semibold hover:bg-[#005b56] disabled:opacity-40 transition-colors">
                    <Ic path={PATHS.check} size={14} /> Save
                  </button>
                  <button onClick={() => setEditingTavily(false)}
                    className="text-[13px] text-[#083f3a]/50 hover:text-[#083f3a] px-2 transition-colors">Cancel</button>
                </div>
              </div>
            )}
          </div>
        </div>

        <p className="mt-4 flex items-center gap-1.5 text-[12px] text-[#083f3a]/40">
          <Ic path={PATHS.shield} size={12} /> Keys never leave your account and are encrypted at rest.
        </p>
      </div>
      <style>{`@keyframes slideUp { from { transform: translateY(34px); opacity: 0.25; } to { transform: translateY(0); opacity: 1; } }`}</style>
    </div>
  );
}

// ── End confirm dialog ─────────────────────────────────────────────────────

function EndConfirmDialog({ remainingLabel, onConfirm, onCancel }: {
  remainingLabel: string; onConfirm: () => void; onCancel: () => void;
}) {
  return (
    <div className="absolute inset-0 z-45 flex items-end bg-[rgba(8,40,38,0.42)] backdrop-blur-[2px]"
      onClick={onCancel}>
      <div className="w-full bg-white text-[#083f3a] rounded-t-[22px] px-5 pt-6 pb-5"
        onClick={(e) => e.stopPropagation()}
        style={{ animation: "slideUp 0.3s cubic-bezier(0.2,0.8,0.2,1) both" }}>
        <h3 className="font-['Playfair_Display'] text-[22px] font-semibold leading-[1.18] mb-2">End the session early?</h3>
        <p className="text-[13.5px] text-[#083f3a]/60 leading-[1.5] mb-4">
          {remainingLabel && `There's still ${remainingLabel} left. `}I'll do a short close — gather what became clear and ask whether to keep it — so nothing is lost.
        </p>
        <div className="flex gap-2">
          <button onClick={onConfirm}
            className="flex-1 py-3 rounded-xl bg-[#006d67] text-white text-[14px] font-semibold hover:bg-[#005b56] transition-colors">
            End &amp; wrap up
          </button>
          <button onClick={onCancel}
            className="flex-1 py-3 rounded-xl border border-[#e6e4df] text-[#083f3a] text-[14px] font-medium hover:border-[#006d67]/40 transition-colors">
            Keep going
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Copy button ────────────────────────────────────────────────────────────

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      // Fallback for non-secure contexts
      const ta = document.createElement("textarea");
      ta.value = text;
      ta.style.position = "fixed";
      ta.style.opacity = "0";
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand("copy"); } catch { /* ignore */ }
      document.body.removeChild(ta);
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };
  return (
    <button onClick={copy}
      className="inline-flex items-center gap-1 self-start text-[11.5px] text-white/55 hover:text-white opacity-0 group-hover:opacity-100 focus:opacity-100 transition-opacity -mt-1"
      title="Copy message">
      <Ic path={copied ? PATHS.check : PATHS.copy} size={12} />
      {copied ? "Copied" : "Copy"}
    </button>
  );
}

// ── Composer ───────────────────────────────────────────────────────────────

function Composer({ onSend, placeholder, busy, onStop }: {
  onSend: (text: string) => void;
  placeholder: string;
  busy: boolean;
  onStop: () => void;
}) {
  const [v, setV] = useState("");
  const ref = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 128) + "px";
  }, [v]);

  const fire = () => {
    const t = v.trim();
    if (!t) return;
    onSend(t);
    setV("");
  };

  return (
    <div className="px-3 pb-3 pt-1 sm:px-4 sm:pb-4">
      <div className="flex items-end gap-2 rounded-2xl border border-white/35 bg-white px-4 py-2.5 shadow-sm transition-[border-color,box-shadow] focus-within:border-white focus-within:ring-[3px] focus-within:ring-white/20">
        <textarea
          ref={ref}
          value={v}
          rows={1}
          placeholder={placeholder}
          onChange={(e) => setV(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); fire(); }
          }}
          className="flex-1 bg-transparent resize-none outline-none text-[14.5px] leading-[1.45] text-[#083f3a] placeholder:text-[#083f3a]/40 max-h-32 py-1"
        />
        {busy ? (
          <button onClick={onStop}
            className="w-9 h-9 shrink-0 grid place-items-center rounded-[11px] bg-[#006d67] text-white hover:bg-[#005b56] transition-colors"
            title="Stop">
            <span className="w-3 h-3 rounded-sm bg-white" />
          </button>
        ) : (
          <button onClick={fire} disabled={!v.trim()}
            className="w-9 h-9 shrink-0 grid place-items-center rounded-[11px] bg-[#006d67] text-white disabled:opacity-40 hover:bg-[#005b56] transition-colors"
            title="Send">
            <Ic path={PATHS.chevron} size={16} className="rotate-180" />
          </button>
        )}
      </div>
    </div>
  );
}

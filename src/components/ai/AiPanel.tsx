import {
  useEffect,
  useRef,
  useState,
  useCallback,
  type ReactNode,
} from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { X, ArrowUp } from "lucide-react";
import {
  Drawer,
  DrawerContent,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { useNavigate } from "@tanstack/react-router";
import { useIsMobile } from "@/hooks/use-mobile";
import { ToolRenderer } from "@/components/tools/ToolRenderer";
import { columnLabel, formatCell } from "@/components/tools/tool-logic";
import {
  createTool,
  updateTool,
  addRecord,
  updateRecord,
  deleteRecord,
  parseSchema,
  type Tool,
} from "@/lib/spira/tools-api";
import { useTools, useToolWindows } from "@/components/tools/tools-store";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";
import { useAi } from "./ai-store";
import { useSpira } from "@/lib/spira/store";
import { cn } from "@/lib/utils";
import type { AiAction, Goal } from "@/lib/spira/types";
import { toast } from "sonner";
import {
  streamChat,
  saveApiKey,
  listApiKeys,
  updateKeyModel,
  fetchProviderModels,
  approveProposal,
  rejectProposal,
  saveSessionMemory,
  listGoalProposals,
  type HistoryEntry,
} from "./ai-api";
import {
  type ProposalKind,
  type Proposal,
  uid,
  stripHtml,
  fmtDeadline,
  dedupCreates,
  isOptionActivate,
  createAspects,
  createSummary,
  applyExcludedAspects,
  proposalFromToolArgs,
} from "./proposal-logic";

// ── Types ─────────────────────────────────────────────────────────────────

/** A Personal Tool the AI proposed (preview + approve before it's created). */
type ToolProposal = {
  id: string;
  /** "create" = a brand-new tool; "update" = restructure an existing one. */
  op: "create" | "update";
  /** For op="update": the existing tool's id to PATCH. */
  toolId?: number;
  name: string;
  placement: string;
  goalId?: number;
  schema: unknown;
  /** Initial rows the user supplied up-front; created with the tool on accept. */
  records?: Record<string, unknown>[];
  reasoning?: string;
  status: "pending" | "created" | "dismissed";
};

/** An AI-proposed change to existing tool DATA (edit/delete a row). */
type ToolDataProposal = {
  id: string;
  op: "edit" | "delete";
  toolId: number;
  recordId: number;
  data?: unknown;
  status: "pending" | "applied" | "dismissed";
};

type Msg = {
  id: string;
  role: "user" | "assistant" | "system" | "end";
  content: string;
  streaming?: boolean;
  proposals?: Proposal[];
  toolProposals?: ToolProposal[];
  toolDataProposals?: ToolDataProposal[];
  error?: boolean; // an error bubble — rendered with a warning icon, excluded from history
  /** Ephemeral progress line (GROW library indexing). Display-only: never part
   *  of content, so it can't leak into the transcript or the model history. */
  status?: string;
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
  {
    id: "ANTHROPIC",
    vendor: "Anthropic",
    context: "200 000 tokens",
    connected: false,
    keyPrefix: "sk-ant-",
    activeModel: "claude-sonnet-4-6",
    models: [
      "claude-sonnet-4-6",
      "claude-opus-4-8",
      "claude-haiku-4-5-20251001",
    ],
  },
  {
    id: "OPENAI",
    vendor: "OpenAI",
    context: "128 000 tokens",
    connected: false,
    keyPrefix: "sk-",
    activeModel: "gpt-4o",
    models: ["gpt-4o", "gpt-4o-mini", "o3", "o4-mini"],
  },
  {
    id: "MISTRAL",
    vendor: "Mistral",
    context: "128 000 tokens",
    connected: false,
    keyPrefix: "",
    activeModel: "mistral-large-latest",
    models: [
      "mistral-large-latest",
      "mistral-small-latest",
      "codestral-latest",
      "open-mixtral-8x7b",
      "open-mistral-7b",
    ],
  },
  {
    id: "OLLAMA",
    vendor: "Ollama Cloud",
    context: "cloud",
    connected: false,
    keyPrefix: "",
    activeModel: "gpt-oss:120b",
    models: [
      "gpt-oss:120b",
      "gpt-oss:20b",
      "qwen3-coder:480b",
      "deepseek-v3.1:671b",
    ],
  },
];

// Scroll a goal-page section into view after navigation/panel-close settles. Retries
// briefly in case the page is still mounting (e.g. navigating to a different goal).
function scrollToSection(sectionId: string) {
  let tries = 0;
  const attempt = () => {
    const el = document.getElementById(sectionId);
    if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
    else if (tries++ < 12) setTimeout(attempt, 100);
  };
  setTimeout(attempt, 120);
}

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

const chatScopeKey = (goalId?: string) =>
  `${CHAT_STORE_PREFIX}${goalId ?? "global"}`;

// When the All-Goals chat sends the user to a goal (because the change can only be made
// inside it), we stash their original request here, keyed by goal id. The goal's chat
// picks it up on open and re-sends it — so a card appears immediately instead of an empty
// chat. Read-once: cleared as soon as it's consumed.
const pendingInstrKey = (goalId: string) => `spira.ai.handoff.${goalId}`;
function stashHandoff(goalId: string, text: string) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(pendingInstrKey(goalId), text);
  } catch {
    /* ignore */
  }
}
function takeHandoff(goalId: string): string | undefined {
  if (typeof window === "undefined") return undefined;
  try {
    const k = pendingInstrKey(goalId);
    const v = window.localStorage.getItem(k);
    if (v) window.localStorage.removeItem(k);
    return v || undefined;
  } catch {
    return undefined;
  }
}

// ── Undecided session-end persistence ───────────────────────────────────────
// GROW transcripts are ephemeral, but the END of a session is a decision the
// user must make explicitly. If the page reloads (or the tab closes) before
// they choose Save / Don't save, the pending decision — with the memory draft
// — is restored from localStorage and the card stays until they decide.

const GROW_END_PREFIX = "spira:ai:grow-pending-end:";
const growEndKey = (goalId?: string) =>
  `${GROW_END_PREFIX}${goalId ?? "global"}`;

function loadPendingEnd(goalId?: string): string | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(growEndKey(goalId));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { draft?: string };
    return parsed.draft?.trim() ? parsed.draft : null;
  } catch {
    return null;
  }
}

function savePendingEnd(goalId: string | undefined, draft: string) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(
      growEndKey(goalId),
      JSON.stringify({ draft, ts: Date.now() }),
    );
  } catch {
    /* quota / unavailable — the in-memory card still works */
  }
}

function clearPendingEnd(goalId?: string) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.removeItem(growEndKey(goalId));
  } catch {
    /* ignore */
  }
}

// ── Live GROW session persistence ───────────────────────────────────────────
// An accidentally closed tab must not kill a running session: the transcript
// and the session's real END TIME (wall clock — the timer keeps running while
// the tab is closed) are cached per goal. On reopen, the session resumes if
// time remains; if it ran out while away, the normal closing flow fires.

const GROW_SESSION_PREFIX = "spira:ai:grow-session:";
const growSessionKey = (goalId?: string) =>
  `${GROW_SESSION_PREFIX}${goalId ?? "global"}`;

type StoredGrowSession = {
  mins: number;
  total: number;
  endsAt: number;
  msgs: Msg[];
};

function loadGrowSession(goalId?: string): StoredGrowSession | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(growSessionKey(goalId));
    if (!raw) return null;
    const s = JSON.parse(raw) as StoredGrowSession;
    if (
      typeof s.endsAt !== "number" ||
      typeof s.total !== "number" ||
      !Array.isArray(s.msgs)
    ) {
      return null;
    }
    return s;
  } catch {
    return null;
  }
}

function saveGrowSession(goalId: string | undefined, data: StoredGrowSession) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(growSessionKey(goalId), JSON.stringify(data));
  } catch {
    /* ignore */
  }
}

function clearGrowSession(goalId?: string) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.removeItem(growSessionKey(goalId));
  } catch {
    /* ignore */
  }
}

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
    const settled = msgs.filter((m) => !m.streaming).slice(-CHAT_MAX_MESSAGES);
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
  { id: "new-goal", icon: "trophy", text: "Help me create a new goal" },
  {
    id: "edit",
    icon: "pencil",
    text: "Change a goal's confidence or deadline",
  },
  { id: "delete", icon: "trash", text: "Delete a goal" },
];

function buildGoalSuggestions(
  goal: import("@/lib/spira/types").Goal,
): Suggestion[] {
  const s: Suggestion[] = [];

  const deadlineDays = goal.deadline
    ? Math.ceil((new Date(goal.deadline).getTime() - Date.now()) / 86_400_000)
    : null;
  const totalTargets = goal.targets.length;
  const doneTargets = goal.targets.filter(
    (t) => (t.type === "binary" && t.done) || !!t.achievedAt,
  ).length;

  if (goal.confidence <= 3)
    s.push({
      id: "confidence",
      icon: "brain",
      text: "My confidence is low — help me identify what's blocking me",
    });

  if (deadlineDays !== null && deadlineDays > 0 && deadlineDays <= 14)
    s.push({
      id: "deadline",
      icon: "clock",
      text: `${deadlineDays} day${deadlineDays === 1 ? "" : "s"} left — let's decide what still matters`,
    });

  if (goal.reality.obstacles.length > 0)
    s.push({
      id: "obstacles",
      icon: "shield",
      text: `I'm facing ${goal.reality.obstacles.length} obstacle${goal.reality.obstacles.length > 1 ? "s" : ""} — help me think through them`,
    });

  if (doneTargets > 0 && doneTargets < totalTargets)
    s.push({
      id: "progress",
      icon: "trending",
      text: `${doneTargets}/${totalTargets} targets done — what should I focus on next?`,
    });

  if (totalTargets === 0)
    s.push({
      id: "targets",
      icon: "target",
      text: "Help me define concrete targets for this goal",
    });

  if (goal.options.length === 0 && s.length < 3)
    s.push({
      id: "options",
      icon: "switch_",
      text: "What are my strategic options?",
    });

  if (goal.reality.actions.length === 0 && s.length < 3)
    s.push({
      id: "action",
      icon: "zap",
      text: "What's the best next action I can take today?",
    });

  if (s.length === 0)
    s.push(
      {
        id: "reflect",
        icon: "brain",
        text: "Help me think through where I'm stuck",
      },
      {
        id: "reality",
        icon: "leaf",
        text: "What's actually true about this goal right now?",
      },
      {
        id: "options",
        icon: "switch_",
        text: "What are my options from here?",
      },
    );

  return s.slice(0, 3);
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
  const d = m
    ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
    : new Date(v);
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
  const close = useAi((s) => s.close);
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
      <span className="text-[27px] font-extrabold leading-none tracking-[-0.5px]">
        spira
      </span>
      <span className="text-[16px] font-normal leading-none text-white/74 pt-0.5">
        ai coach
      </span>
    </>
  );
}

// ── Panel content (state machine) ──────────────────────────────────────────

function PanelContent({ onClose }: { onClose: () => void }) {
  const { context } = useAi();
  const navigate = useNavigate();
  const goals = useSpira((s) => s.goals);
  const goal = useSpira((s) => s.goals.find((g) => g.id === context.goalId));
  const deleteGoal = useSpira((s) => s.deleteGoal);
  const removeTarget = useSpira((s) => s.removeTarget);
  const removeOption = useSpira((s) => s.removeOption);
  const removeReality = useSpira((s) => s.removeReality);
  const addGoal = useSpira((s) => s.addGoal);
  // AI-initiated deletion never deletes directly — it opens this confirmation dialog.
  const [pendingDelete, setPendingDelete] = useState<{
    kind: "goal" | "target";
    id: string;
    goalId?: string;
  } | null>(null);
  // Long proposal content (note text / goal description) is shown in this modal so
  // the user can read all of it — the card only has room for a title.
  const [contentModal, setContentModal] = useState<{
    title: string;
    body: string;
    html?: boolean;
  } | null>(null);
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

  const applyProposal = useCallback(
    (p: Proposal) => {
      // Creating a new goal works without a "current goal" — it's the primary
      // action of the global / All-Goals chat. Handle it before the guard below.
      if (p.kind === "new_goal") {
        const iso = normalizeDeadline(p.deadline);
        const title = (p.title ?? "").trim().slice(0, 200) || "New goal";
        const conf =
          p.confidence != null && p.confidence >= 1 && p.confidence <= 10
            ? (p.confidence as import("@/lib/spira/types").Confidence)
            : undefined;
        addGoal({
          title,
          description: p.body ?? "",
          ...(conf ? { confidence: conf } : {}),
          ...(iso ? { deadline: iso } : {}),
        });
        toast.success("Goal created");
        return;
      }
      // ── Goal-level ops by id (work from the All-Goals page, no current goal) ──
      if (p.kind === "edit_goal") {
        if (!p.goalId) return;
        const v = p.rawValue ?? p.title;
        if (p.field === "confidence") {
          const c = parseInt(v);
          if (c >= 1 && c <= 10) {
            updateGoal(p.goalId, {
              confidence: c as import("@/lib/spira/types").Confidence,
            });
            toast.success("Goal confidence updated");
          }
        } else if (p.field === "deadline") {
          updateGoal(p.goalId, { deadline: normalizeDeadline(v) });
          toast.success("Goal deadline updated");
        } else {
          updateGoal(p.goalId, { title: (v ?? "").trim().slice(0, 200) });
          toast.success("Goal renamed");
        }
        return;
      }
      if (p.kind === "open_goal") {
        if (p.goalId)
          navigate({ to: "/goals/$goalId", params: { goalId: p.goalId } });
        return;
      }
      if (p.kind === "delete_goal") {
        const gid = p.goalId ?? context.goalId;
        if (gid && goals.some((x) => x.id === gid))
          setPendingDelete({ kind: "goal", id: gid });
        else toast.error("I couldn't find that goal to delete.");
        return;
      }
      if (p.kind === "delete_target") {
        // Only a REAL target can be deleted. The model sometimes fires delete_target for an
        // option / obstacle / action / checklist item (which it can't delete) with an id that
        // matches no target — guard against that so we never open a phantom dialog or no-op.
        const gid = p.goalId ?? context.goalId;
        const g = goals.find((x) => x.id === gid);
        if (gid && p.itemId && g?.targets.some((t) => t.id === p.itemId)) {
          setPendingDelete({ kind: "target", id: p.itemId, goalId: gid });
        } else {
          toast.error(
            "I can only delete a whole target. Options, obstacles, actions and checklist items are removed with the × next to the item.",
          );
        }
        return;
      }
      if (!goal) return;
      // Resource titles are labels — the backend rejects > 200 chars (which
      // silently rolled back AI-created notes whose title was long). Clamp to fit.
      const label = (s: string | undefined) =>
        (s ?? "").trim().slice(0, 200) || "Note";
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
            updateGoal(goal.id, {
              confidence: c as import("@/lib/spira/types").Confidence,
            });
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
              ...(it.deadline
                ? { deadline: normalizeDeadline(it.deadline) }
                : {}),
            }));
            addTarget(goal.id, {
              type: "checklist",
              title: p.title,
              items,
              ...(iso ? { deadline: iso } : {}),
            });
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
            addTarget(goal.id, {
              type: "binary",
              title: p.title,
              done: false,
              ...(iso ? { deadline: iso } : {}),
            }).then((created) => {
              if (created && p.done)
                updateTarget(goal.id, created.id, { done: true });
            });
            toast.success(p.done ? "Target added & completed" : "Target added");
          }
          break;
        }
        case "option":
          // `done` here means "make it the selected option on create" — a new option
          // has no id yet, so we select it once the server returns the real one.
          addOption(
            goal.id,
            p.title,
            p.done ? (created) => selectOption(goal.id, created.id) : undefined,
          );
          toast.success(p.done ? "Option added & selected" : "Option added");
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
          addResource(goal.id, {
            type: "note",
            title: label(p.title),
            body: p.body ?? "",
          });
          toast.success("Note saved");
          break;
        case "link": {
          const url = (p.patch?.url ?? "").trim();
          // A link resource needs a web address. Don't fail silently (which looked like
          // "the card did nothing") — tell the user. To rename an existing link the AI must
          // use edit_link with its id, not create a new one.
          if (!url) {
            toast.error(
              "A link needs a web address (URL). To rename an existing link, ask me to edit it.",
            );
            break;
          }
          // Empty title is intentional — the backend derives a label from the domain.
          const linkTitle = (p.patch?.title ?? "").trim().slice(0, 200);
          addResource(goal.id, { type: "link", title: linkTitle, url });
          toast.success("Link added");
          break;
        }
        case "email": {
          const email = p.patch?.email?.trim();
          // Empty name is intentional — the backend derives it from the email address.
          const contactName = (p.patch?.name ?? "").trim().slice(0, 200);
          addResource(goal.id, {
            type: "email",
            name: contactName,
            ...(email ? { email } : {}),
            ...(p.patch?.role ? { role: p.patch.role } : {}),
            ...(p.patch?.phone ? { phone: p.patch.phone } : {}),
          });
          toast.success("Contact added");
          break;
        }

        // ── edit existing items ──
        case "edit_target": {
          if (!p.itemId) break;
          // Text is required — never let an "edit" blank it out (the AI must use delete_* to
          // remove things, not erase the text).
          if (!p.title.trim()) {
            toast.error("A target needs a name — use delete to remove it.");
            break;
          }
          const iso = normalizeDeadline(p.deadline);
          updateTarget(goal.id, p.itemId, {
            title: p.title,
            ...(iso ? { deadline: iso } : {}),
          });
          toast.success("Target updated");
          break;
        }
        case "edit_option":
          if (!p.itemId) break;
          if (!p.title.trim()) {
            toast.error("An option needs text — use delete to remove it.");
            break;
          }
          updateOption(goal.id, p.itemId, { text: p.title });
          toast.success("Option updated");
          break;
        case "edit_obstacle":
          if (!p.itemId) break;
          if (!p.title.trim()) {
            toast.error("An obstacle needs text — use delete to remove it.");
            break;
          }
          updateReality(goal.id, "obstacles", p.itemId, p.title);
          toast.success("Obstacle updated");
          break;
        case "edit_action":
          if (!p.itemId) break;
          if (!p.title.trim()) {
            toast.error("An action needs text — use delete to remove it.");
            break;
          }
          updateReality(goal.id, "actions", p.itemId, p.title);
          toast.success("Action updated");
          break;
        case "edit_note":
          if (p.itemId) {
            updateResource(goal.id, p.itemId, {
              title: label(p.title),
              body: p.body ?? "",
            });
            toast.success("Note updated");
          }
          break;
        case "edit_link":
          if (p.itemId && p.patch && Object.keys(p.patch).length) {
            updateResource(
              goal.id,
              p.itemId,
              p.patch as Partial<import("@/lib/spira/types").Resource>,
            );
            toast.success("Link updated");
          }
          break;
        case "edit_email":
          if (p.itemId && p.patch && Object.keys(p.patch).length) {
            updateResource(
              goal.id,
              p.itemId,
              p.patch as Partial<import("@/lib/spira/types").Resource>,
            );
            toast.success("Contact updated");
          }
          break;

        // ── state changes ──
        case "complete_target":
          if (p.itemId) {
            updateTarget(goal.id, p.itemId, { done: p.done !== false });
            toast.success("Target updated");
          }
          break;
        case "target_progress": {
          if (!p.itemId) break;
          const n = Number(p.rawValue ?? p.title);
          if (!Number.isNaN(n)) {
            updateTarget(goal.id, p.itemId, { current: n });
            toast.success("Progress updated");
          }
          break;
        }
        case "select_option":
          if (p.itemId) {
            selectOption(goal.id, p.itemId);
            toast.success("Option selected");
          }
          break;
        case "checklist_item": {
          if (!p.itemId) break;
          const parent = goal.targets.find(
            (t) =>
              t.type === "checklist" && t.items.some((i) => i.id === p.itemId),
          );
          if (parent && parent.type === "checklist") {
            const iso = normalizeDeadline(p.deadline);
            const items = parent.items.map((i) =>
              i.id === p.itemId
                ? {
                    ...i,
                    ...(p.rawValue ? { text: p.rawValue } : {}), // rawValue = real new text (title may be a placeholder)
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
          updateTarget(goal.id, parent.id, {
            items: [...parent.items, newItem],
          });
          toast.success("Sub-task added");
          break;
        }

        // ── delete smaller items (the card's Accept is the confirmation) ──
        case "delete_option":
          if (p.itemId && goal.options.some((o) => o.id === p.itemId)) {
            removeOption(goal.id, p.itemId);
            toast.success("Option deleted");
          } else toast.error("I couldn't find that option to delete.");
          break;
        case "delete_obstacle":
          if (
            p.itemId &&
            goal.reality.obstacles.some((o) => o.id === p.itemId)
          ) {
            removeReality(goal.id, "obstacles", p.itemId);
            toast.success("Obstacle deleted");
          } else toast.error("I couldn't find that obstacle to delete.");
          break;
        case "delete_action":
          if (p.itemId && goal.reality.actions.some((a) => a.id === p.itemId)) {
            removeReality(goal.id, "actions", p.itemId);
            toast.success("Action deleted");
          } else toast.error("I couldn't find that action to delete.");
          break;
        case "delete_checklist_item": {
          if (!p.itemId) break;
          const parent = goal.targets.find(
            (t) =>
              t.type === "checklist" && t.items.some((i) => i.id === p.itemId),
          );
          if (parent && parent.type === "checklist") {
            updateTarget(goal.id, parent.id, {
              items: parent.items.filter((i) => i.id !== p.itemId),
            });
            toast.success("Sub-task deleted");
          } else toast.error("I couldn't find that checklist item to delete.");
          break;
        }
      }
    },
    [
      goal,
      goals,
      addGoal,
      updateGoal,
      addTarget,
      addOption,
      addReality,
      addResource,
      updateTarget,
      updateOption,
      updateReality,
      updateResource,
      selectOption,
      removeOption,
      removeReality,
      navigate,
      context.goalId,
    ],
  );

  // Deletion is destructive, so we don't render a confirmation card for it — the
  // proper delete dialog already shows exactly what will be removed. As soon as the
  // AI proposes a delete we open that dialog and drop the proposal from the chat,
  // returning only the proposals that should appear as cards.
  const openDeletesAndFilter = useCallback(
    (proposals: Proposal[]): Proposal[] => {
      // applyProposal validates the goal/target exists: it opens the confirm dialog only for a
      // real one, otherwise it shows an explanatory toast (the model sometimes fires
      // delete_target for an option/obstacle/action it can't actually delete).
      const del = proposals.find(
        (p) => p.kind === "delete_goal" || p.kind === "delete_target",
      );
      if (del) applyProposal(del);
      return proposals.filter(
        (p) => p.kind !== "delete_goal" && p.kind !== "delete_target",
      );
    },
    [applyProposal],
  );

  // Creates the goal/target and reports a createdRef (kind + goal id) via onRef so the
  // caller can stamp it on the proposal — that persists the "Open …" shortcut.
  const onCreateProposal = useCallback(
    (
      edited: Proposal,
      onRef: (ref: { kind: "goal" | "target"; goalId: string }) => void,
    ) => {
      if (edited.kind === "new_goal") {
        const iso = normalizeDeadline(edited.deadline);
        const title = (edited.title ?? "").trim().slice(0, 200) || "New goal";
        const conf =
          edited.confidence != null &&
          edited.confidence >= 1 &&
          edited.confidence <= 10
            ? (edited.confidence as import("@/lib/spira/types").Confidence)
            : undefined;
        // The real id only exists after the server sync, so report it from onCreated
        // (a failed save simply never reports → no button).
        addGoal(
          {
            title,
            description: edited.body ?? "",
            ...(conf ? { confidence: conf } : {}),
            ...(iso ? { deadline: iso } : {}),
          },
          (created) => onRef({ kind: "goal", goalId: created.id }),
        );
        toast.success("Goal created");
        return;
      }
      // target / task — applyProposal handles all three target shapes; the target lives
      // on the current goal's page, so the shortcut opens it there.
      applyProposal(edited);
      const gid = context.goalId;
      if (gid) onRef({ kind: "target", goalId: gid });
    },
    [addGoal, applyProposal, context.goalId],
  );

  // Open just-created content: close the chat, go to the goal page, and scroll to the
  // relevant section (targets → "Will do", resources → "Resources").
  const onOpenCreated = useCallback(
    (ref: { kind: "goal" | "target" | "resource"; goalId: string }) => {
      onClose();
      navigate({ to: "/goals/$goalId", params: { goalId: ref.goalId } });
      if (ref.kind === "target") scrollToSection("targets-section");
      else if (ref.kind === "resource") scrollToSection("resources-section");
    },
    [onClose, navigate],
  );

  const scopeKey = chatScopeKey(context.goalId);

  const [mode, setMode] = useState<Mode>("chat");
  const [msgs, setMsgs] = useState<Msg[]>(() => loadTranscript(scopeKey));
  const [gmsgs, setGmsgs] = useState<Msg[]>([]);
  const [busy, setBusy] = useState(false);
  const [session, setSession] = useState<{
    total: number;
    remaining: number;
    mins: number;
  } | null>(null);
  const [showProvider, setShowProvider] = useState(false);
  const [confirmEnd, setConfirmEnd] = useState(false);
  // What "Save memory" will persist — previewed and revisable on the end card.
  // Initialised from localStorage: an undecided session end survives reloads.
  const [memoryDraft, setMemoryDraft] = useState<string | null>(() =>
    loadPendingEnd(context.goalId),
  );
  const [memoryRevising, setMemoryRevising] = useState(false);
  const [providers, setProviders] = useState<ProviderInfo[]>(PROVIDERS_DEFAULT);
  const [activeProv, setActiveProv] = useState(
    () => readSavedProvider() || "ANTHROPIC",
  );
  const [tavily, setTavily] = useState<{ connected: boolean; hint?: string }>({
    connected: false,
  });

  const stopRef = useRef(false);
  const endedRef = useRef(false);
  // The timer ran out and the closing turn was requested — guards double-sends
  // while the seconds keep ticking past zero.
  const wrapUpRef = useRef(false);
  // Composer draft survives unmounts (e.g. the end-of-session card replacing
  // the input) — an unfinished message must never silently disappear.
  const draftRef = useRef("");
  const scrollRef = useRef<HTMLDivElement>(null);
  // In-place card revision ("Type a change for the AI…"): shows a cancellable "Revising…"
  // state in the footer so a stalled revise is never a dead-end (no Stop button otherwise).
  const reviseTokenRef = useRef(0);
  const [revising, setRevising] = useState<{
    token: number;
    label: string;
  } | null>(null);
  const cancelRevise = () => {
    reviseTokenRef.current++;
    setRevising(null);
    setBusy(false);
  };

  const inGrow =
    mode === "grow-active" || mode === "grow-closing" || mode === "grow-end";
  const list = inGrow ? gmsgs : msgs;

  // A pending proposal card IS the input — it renders in the footer (where the
  // composer would be) instead of inline, so it sits right above the keyboard.
  const pendingMsg = list.find((m) =>
    m.proposals?.some((pr) => pr.status === "pending"),
  );

  // Load saved keys on mount
  useEffect(() => {
    listApiKeys()
      .then(
        (keys: Array<{ provider: string; hint: string; model: string }>) => {
          if (!keys.length) return;
          setProviders((ps) =>
            ps.map((p) => {
              const found = keys.find((k) => k.provider === p.id);
              if (!found) return p;
              return {
                ...p,
                connected: true,
                keyHint: found.hint,
                activeModel: found.model || p.activeModel,
              };
            }),
          );
          const tav = keys.find((k) => k.provider === "TAVILY");
          if (tav) setTavily({ connected: true, hint: tav.hint });
          // Active chat provider must be an LLM — never Tavily (a search key).
          // Honour the user's last choice if that provider still has a key;
          // otherwise fall back to the first available LLM key.
          const saved = readSavedProvider();
          const savedHasKey =
            !!saved &&
            saved !== "TAVILY" &&
            keys.some((k) => k.provider === saved);
          if (!savedHasKey) {
            const firstLlm = keys.find((k) => k.provider !== "TAVILY");
            if (firstLlm) {
              setActiveProv(firstLlm.provider);
              saveActiveProvider(firstLlm.provider);
            }
          }
        },
      )
      .catch(() => {
        /* backend not running – ignore */
      });
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
    // Each scope carries its own undecided session end (if any). Never clobber
    // a live session's draft — scope switches don't happen mid-grow.
    if (!inGrow) setMemoryDraft(loadPendingEnd(context.goalId));
    // eslint-disable-next-line react-hooks/exhaustive-deps
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

  // ── pending proposal restore ──────────────────────────────────────────────
  // A card can vanish from the UI while its proposal is still PENDING on the
  // server: GROW transcripts are ephemeral, chat history is capped, and
  // localStorage can be cleared. On mount / goal switch, fetch the goal's
  // pending proposals and re-surface any the restored transcript no longer
  // carries. Once shown, the message persists like any other, so this never
  // duplicates a card it already restored.
  useEffect(() => {
    const goalId = context.goalId;
    if (!goalId) return;
    let cancelled = false;
    listGoalProposals(goalId)
      .then((server) => {
        if (cancelled || server.length === 0) return;
        setMsgs((prev) => {
          const known = new Set(
            prev
              .flatMap((m) => (m.proposals ?? []).map((p) => p.serverId))
              .filter((id): id is number => id != null),
          );
          const restored = server
            .filter((sp) => sp.status === "PENDING" && !known.has(sp.id))
            .flatMap((sp) => {
              const p = proposalFromToolArgs(sp.payload);
              if (!p) return [];
              p.serverId = sp.id;
              // Same enrichment as the live onProposal path: goal-level ops
              // need the goal's name to render; unresolvable ones are dropped.
              if (p.goalId && !p.goalTitle) {
                const g = goals.find((x) => x.id === p.goalId);
                if (g) p.goalTitle = g.title;
              }
              if (
                (p.kind === "edit_goal" || p.kind === "open_goal") &&
                !p.goalTitle
              )
                return [];
              return [p];
            });
          if (restored.length === 0) return prev;
          return [
            ...prev,
            {
              id: uid(),
              role: "assistant" as const,
              content:
                "These proposals from an earlier session are still waiting for your review.",
              proposals: restored,
            },
          ];
        });
      })
      .catch(() => {
        /* restore is best-effort — the chat works without it */
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [context.goalId]);

  // (We deliberately do NOT re-surface still-pending server proposals on open: the
  // local transcript already restores the cards, and pulling every unresolved row
  // from past turns resurfaced stale/irrelevant cards.)

  // Clears the visible chat AND its saved transcript for this scope, so the
  // next message is sent with NO history — context comes only from the goal's
  // data. Past mistakes in the conversation stop leaking into the model.
  const newChat = () => {
    if (busy) return;
    setMsgs([]);
    try {
      window.localStorage.removeItem(scopeKey);
    } catch {
      /* ignore */
    }
    toast.success(
      "Started a fresh chat — only the goal's data is in context now",
    );
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
      .filter(
        (m) =>
          (m.role === "user" || m.role === "assistant") &&
          m.content.trim() &&
          !m.error,
      )
      .map((m) => ({
        role: m.role as "user" | "assistant",
        content: m.content,
      }));

    const id = uid();
    setMsgs((p) => [
      ...p,
      { id, role: "assistant", content: "", streaming: true },
    ]);
    let accumulated = "";
    const pendingProposals: Proposal[] = [];
    const pendingToolProposals: ToolProposal[] = [];
    const pendingToolDataProposals: ToolDataProposal[] = [];

    streamChat({
      goalId: context.goalId,
      message: text,
      history,
      provider: activeProv,
      sessionType: "chat",
      onToken: (tok) => {
        if (stopRef.current) return;
        accumulated += tok;
        setMsgs((p) =>
          p.map((m) => (m.id === id ? { ...m, content: accumulated } : m)),
        );
        scrollRef.current?.scrollTo({ top: 99999, behavior: "smooth" });
      },
      onToolDataProposal: (argsJson) => {
        try {
          const t = JSON.parse(argsJson);
          if (
            t &&
            (t.op === "edit" || t.op === "delete") &&
            t.toolId &&
            t.recordId
          ) {
            pendingToolDataProposals.push({
              id: uid(),
              op: t.op,
              toolId: t.toolId,
              recordId: t.recordId,
              data: t.data,
              status: "pending",
            });
          }
        } catch {
          /* malformed — ignore */
        }
      },
      onToolProposal: (argsJson) => {
        try {
          const t = JSON.parse(argsJson);
          if (t && t.schema) {
            pendingToolProposals.push({
              id: uid(),
              op: t.op === "update" ? "update" : "create",
              toolId: typeof t.toolId === "number" ? t.toolId : undefined,
              name: t.name ?? (t.op === "update" ? "" : "Tool"),
              placement: t.placement ?? "tools",
              goalId: typeof t.goalId === "number" ? t.goalId : undefined,
              schema: t.schema,
              records: Array.isArray(t.records) ? t.records : undefined,
              reasoning: t.reasoning,
              status: "pending",
            });
          }
        } catch {
          /* malformed — ignore */
        }
      },
      onProposal: (argsJson) => {
        const p = proposalFromToolArgs(argsJson);
        if (!p) return;
        // Goal-level ops (edit/open/delete) carry only the goal id — resolve its name so
        // the card can show WHICH goal is being changed.
        if (p.goalId && !p.goalTitle) {
          const g = goals.find((x) => x.id === p.goalId);
          if (g) p.goalTitle = g.title;
        }
        // An edit/open that names no real goal is unusable — it can't be applied and has no
        // name to show. Drop it so no misleading "This goal" card appears; the AI should
        // have asked which goal instead.
        if ((p.kind === "edit_goal" || p.kind === "open_goal") && !p.goalTitle)
          return;
        pendingProposals.push(p);
      },
      onDone: () => {
        // Deletes open the confirm dialog immediately and never become cards.
        const afterDeletes = openDeletesAndFilter(pendingProposals);
        // Creations are surfaced as cards the user confirms (NOT auto-applied), so they
        // can review each one. Honour every distinct create the model proposes — the user
        // can ask for several at once — dropping only exact duplicates; reject those
        // duplicates server-side so they don't resurface.
        const allCreates = afterDeletes.filter((pp) =>
          CREATE_KINDS.has(pp.kind),
        );
        const creates = dedupCreates(allCreates);
        allCreates
          .filter((pp) => !creates.includes(pp))
          .forEach((pp) => {
            if (pp.serverId != null)
              rejectProposal(pp.serverId).catch(() => {});
          });
        const others = afterDeletes.filter((pp) => !CREATE_KINDS.has(pp.kind));
        const finalProposals = [...others, ...creates];
        const content =
          accumulated.trim() ||
          (finalProposals.length ||
          pendingToolProposals.length ||
          pendingToolDataProposals.length
            ? "I've prepared this for your review."
            : "");
        setMsgs((p) =>
          p.map((m) =>
            m.id === id
              ? {
                  ...m,
                  streaming: false,
                  content,
                  ...(finalProposals.length
                    ? { proposals: finalProposals }
                    : {}),
                  ...(pendingToolProposals.length
                    ? { toolProposals: pendingToolProposals }
                    : {}),
                  ...(pendingToolDataProposals.length
                    ? { toolDataProposals: pendingToolDataProposals }
                    : {}),
                }
              : m,
          ),
        );
        // The AI may have added/changed rows server-side (add_tool_record) or via
        // an applied proposal — refresh any open tool windows so changes show now.
        const bump = useTools.getState().bumpRecords;
        useToolWindows.getState().windows.forEach((w) => bump(w.id));
        setBusy(false);
      },
      onError: (err) => {
        setBusy(false);
        if (err === "NO_KEY") {
          setMsgs((p) => p.filter((m) => m.id !== id));
          setShowProvider(true);
          return;
        }
        const msg =
          err === "NETWORK"
            ? "Backend unreachable — is it running?"
            : err || "AI error. Try again.";
        // Show the error in place of the empty streaming bubble — visible and
        // persistent (a transient toast is easy to miss for long messages).
        setMsgs((p) =>
          p.map((m) =>
            m.id === id
              ? { ...m, streaming: false, content: msg, error: true }
              : m,
          ),
        );
        toast.error(msg);
      },
    });
  };

  const stopStream = () => {
    stopRef.current = true;
    setBusy(false);
  };

  // Revise a proposal "in place": when the user types a change on a card ("Type a change for
  // the AI…"), DON'T spawn a new chat turn / new card. Re-ask the model, then swap the new
  // proposal into the SAME message slot (keeping its id) so the original card simply updates.
  // No visible "Revise your proposed…" user bubble, no pile of duplicate cards.
  const reviseInPlace = (
    targetMsgId: string,
    targetProposalId: string,
    p: Proposal,
    instruction: string,
  ) => {
    if (busy) return;
    setBusy(true);
    stopRef.current = false;
    const grow = inGrow;
    const setList = grow ? setGmsgs : setMsgs;
    const curList = grow ? gmsgs : msgs;

    // A token guards against a stalled/late stream mutating the card after the user cancels
    // (or a safety timeout fires). Only the currently-active revise may finish or update.
    const token = ++reviseTokenRef.current;
    const { headline } = proposalDisplay(p, goal);
    setRevising({ token, label: headline });
    const finish = () => {
      setRevising((r) => (r && r.token === token ? null : r));
      setBusy(false);
    };
    // Safety net: a revise must never leave the card frozen with no way out. If the stream
    // never completes, recover automatically.
    const timer = setTimeout(() => {
      if (reviseTokenRef.current !== token) return;
      reviseTokenRef.current++;
      finish();
      toast.error("The AI took too long — the card is unchanged. Try again.");
    }, 90_000);
    const stillActive = () =>
      reviseTokenRef.current === token && !stopRef.current;

    // The old server-side proposal row is superseded — drop it (the new one gets its own id).
    if (p.serverId != null) rejectProposal(p.serverId).catch(() => {});

    const history: HistoryEntry[] = curList
      .filter(
        (m) =>
          (m.role === "user" || m.role === "assistant") &&
          m.content.trim() &&
          !m.error,
      )
      .map((m) => ({
        role: m.role as "user" | "assistant",
        content: m.content,
      }));

    const label = (KIND_META[p.kind]?.label ?? "change").toLowerCase();
    const disp = proposalDisplay(p, goal);
    const ctx = disp.detail
      ? `${disp.headline} (${disp.detail})`
      : disp.headline;
    const message = `Revise your proposed ${label} "${ctx}": ${instruction}. Re-propose it with the change applied — one proposal.`;

    let accumulated = "";
    const pendingProposals: Proposal[] = [];

    streamChat({
      goalId: context.goalId,
      message,
      history,
      provider: activeProv,
      sessionType: grow ? "grow" : "chat",
      onToken: (tok) => {
        if (stillActive()) accumulated += tok;
      },
      onProposal: (argsJson) => {
        if (!stillActive()) return;
        const np = proposalFromToolArgs(argsJson);
        if (!np) return;
        if (np.goalId && !np.goalTitle) {
          const g = goals.find((x) => x.id === np.goalId);
          if (g) np.goalTitle = g.title;
        }
        if (
          (np.kind === "edit_goal" || np.kind === "open_goal") &&
          !np.goalTitle
        )
          return;
        pendingProposals.push(np);
      },
      onDone: () => {
        clearTimeout(timer);
        if (!stillActive()) return; // cancelled / superseded — don't touch the card
        const afterDeletes = openDeletesAndFilter(pendingProposals);
        if (afterDeletes.length === 0) {
          // The model answered with text (e.g. a clarifying question) instead of a revised
          // proposal — surface that so it isn't lost, and leave the original card untouched.
          if (accumulated.trim()) {
            setList((ms) => [
              ...ms,
              {
                id: uid(),
                role: "assistant" as const,
                content: accumulated.trim(),
              },
            ]);
          }
          finish();
          return;
        }
        // First revised proposal takes over the original card's slot (same id); any extras
        // join the SAME message so everything stays on one card group, never a new one.
        const [first, ...rest] = afterDeletes;
        const replaced: Proposal = {
          ...first,
          id: targetProposalId,
          status: "pending",
        };
        const extras = rest.map((r) => ({ ...r, status: "pending" as const }));
        setList((ms) =>
          ms.map((m) => {
            if (m.id !== targetMsgId) return m;
            const nextProposals = (m.proposals ?? []).flatMap((pr) =>
              pr.id === targetProposalId ? [replaced, ...extras] : [pr],
            );
            return { ...m, proposals: nextProposals };
          }),
        );
        finish();
      },
      onError: (err) => {
        clearTimeout(timer);
        if (reviseTokenRef.current !== token) return;
        finish();
        const msg =
          err === "NETWORK"
            ? "Backend unreachable — is it running?"
            : err || "AI error. Try again.";
        toast.error(msg);
      },
    });
  };

  // Keep a live ref to sendChat so the handoff effect can call the latest one without
  // re-firing on every render.
  const sendChatRef = useRef(sendChat);
  useEffect(() => {
    sendChatRef.current = sendChat;
  });

  // When this chat scopes to a goal that was opened from the All-Goals chat with a pending
  // request, re-send that request here so the edit card appears straight away (not an empty
  // chat). Runs once per arrival — takeHandoff clears it.
  useEffect(() => {
    const gid = context.goalId;
    if (!gid) return;
    const instr = takeHandoff(gid);
    if (!instr) return;
    // Let the re-scoped transcript settle first, then send.
    const t = setTimeout(() => sendChatRef.current(instr), 80);
    return () => clearTimeout(t);
  }, [context.goalId]);

  // ── GROW ──────────────────────────────────────────────────────────────────

  const startGrow = (mins: number, focus: string) => {
    // An undecided previous session blocks a new one — its result must not be
    // silently overwritten. The pending card is on screen; decide there first.
    if (memoryDraft) {
      setMode("chat");
      toast.error(
        "Finish the previous session first — save or discard its result below.",
      );
      return;
    }
    setGmsgs([]);
    endedRef.current = false;
    wrapUpRef.current = false;
    setMemoryDraft(null);
    const total = mins * 60;
    setSession({ total, remaining: total, mins });
    setMode("grow-active");
    // The session's real end moment — survives tab closes; the clock keeps
    // running while away, like a real coaching appointment.
    saveGrowSession(context.goalId, {
      mins,
      total,
      endsAt: Date.now() + total * 1000,
      msgs: [],
    });

    const opening = focus ? `I want to work on: ${focus}` : "Let's start.";

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
    setGmsgs((p) => [
      ...p,
      { id, role: "assistant", content: "", streaming: true },
    ]);

    streamChat({
      goalId: context.goalId,
      message: opening,
      history: [],
      provider: activeProv,
      sessionType: "grow",
      sessionTotalMinutes: mins,
      sessionRemainingSeconds: total,
      onStatus: (status) => {
        if (stopRef.current) return;
        setGmsgs((p) => p.map((m) => (m.id === id ? { ...m, status } : m)));
      },
      onToken: (tok) => {
        if (stopRef.current) return;
        accumulated += tok;
        setGmsgs((p) =>
          p.map((m) =>
            m.id === id ? { ...m, content: accumulated, status: undefined } : m,
          ),
        );
        scrollRef.current?.scrollTo({ top: 99999, behavior: "smooth" });
      },
      onDone: () => {
        setGmsgs((p) =>
          p.map((m) =>
            m.id === id ? { ...m, streaming: false, status: undefined } : m,
          ),
        );
        setBusy(false);
      },
      onError: (err) => {
        setBusy(false);
        setGmsgs((p) => p.filter((m) => m.id !== id));
        if (err === "NO_KEY") {
          setShowProvider(true);
          return;
        }
        toast.error(err || "AI error.");
      },
    });
  };

  /**
   * One GROW turn. `wrapUp` is the timer-driven closing turn: the instruction
   * is sent to the model but never shown or kept as a user bubble, and once
   * the coach's goodbye lands the end card follows.
   */
  const sendGrow = (text: string, opts?: { wrapUp?: boolean }) => {
    const wrapUp = opts?.wrapUp ?? false;
    if (busy && !wrapUp) return;
    if (!wrapUp) {
      const userMsg = { id: uid(), role: "user" as const, content: text };
      setGmsgs((p) => [...p, userMsg]);
    }
    setBusy(true);
    stopRef.current = false;

    const history: HistoryEntry[] = gmsgs
      .filter(
        (m) =>
          (m.role === "user" || m.role === "assistant") &&
          m.content.trim() &&
          !m.error,
      )
      .map((m) => ({
        role: m.role as "user" | "assistant",
        content: m.content,
      }));

    const id = uid();
    setGmsgs((p) => [
      ...p,
      { id, role: "assistant", content: "", streaming: true },
    ]);
    let accumulated = "";
    const pendingProposals: Proposal[] = [];

    streamChat({
      goalId: context.goalId,
      message: text,
      history,
      provider: activeProv,
      sessionType: "grow",
      sessionTotalMinutes: session?.mins,
      sessionRemainingSeconds: wrapUp ? 0 : Math.round(session?.remaining ?? 0),
      onStatus: (status) => {
        if (stopRef.current) return;
        setGmsgs((p) => p.map((m) => (m.id === id ? { ...m, status } : m)));
      },
      onToken: (tok) => {
        if (stopRef.current) return;
        accumulated += tok;
        setGmsgs((p) =>
          p.map((m) =>
            m.id === id ? { ...m, content: accumulated, status: undefined } : m,
          ),
        );
        scrollRef.current?.scrollTo({ top: 99999, behavior: "smooth" });
      },
      onProposal: (argsJson) => {
        const p = proposalFromToolArgs(argsJson);
        if (!p) return;
        // Goal-level ops (edit/open/delete) carry only the goal id — resolve its name so
        // the card can show WHICH goal is being changed.
        if (p.goalId && !p.goalTitle) {
          const g = goals.find((x) => x.id === p.goalId);
          if (g) p.goalTitle = g.title;
        }
        // An edit/open that names no real goal is unusable — it can't be applied and has no
        // name to show. Drop it so no misleading "This goal" card appears; the AI should
        // have asked which goal instead.
        if ((p.kind === "edit_goal" || p.kind === "open_goal") && !p.goalTitle)
          return;
        pendingProposals.push(p);
      },
      onDone: () => {
        const afterDeletes = openDeletesAndFilter(pendingProposals);
        const allCreates = afterDeletes.filter((pp) =>
          CREATE_KINDS.has(pp.kind),
        );
        const creates = dedupCreates(allCreates);
        allCreates
          .filter((pp) => !creates.includes(pp))
          .forEach((pp) => {
            if (pp.serverId != null)
              rejectProposal(pp.serverId).catch(() => {});
          });
        const others = afterDeletes.filter((pp) => !CREATE_KINDS.has(pp.kind));
        const finalProposals = [...others, ...creates];
        const content =
          accumulated.trim() ||
          (finalProposals.length ? "I've prepared this for your review." : "");
        setGmsgs((p) =>
          p.map((m) =>
            m.id === id
              ? {
                  ...m,
                  streaming: false,
                  status: undefined,
                  content,
                  ...(finalProposals.length
                    ? { proposals: finalProposals }
                    : {}),
                }
              : m,
          ),
        );
        setBusy(false);
        // The coach has said its goodbye — now the end card may follow, with
        // this very goodbye offered as the session memory draft.
        if (wrapUp) finishGrow(content);
      },
      onError: (err) => {
        setBusy(false);
        if (err === "NO_KEY") {
          setGmsgs((p) => p.filter((m) => m.id !== id));
          setShowProvider(true);
          return;
        }
        const msg =
          err === "NETWORK"
            ? "Backend unreachable — is it running?"
            : err || "AI error.";
        setGmsgs((p) =>
          p.map((m) =>
            m.id === id
              ? { ...m, streaming: false, content: msg, error: true }
              : m,
          ),
        );
        toast.error(msg);
        // Even if the goodbye failed, the session is over — don't strand the user.
        if (wrapUp) finishGrow();
      },
    });
  };

  /**
   * Ends the session. The coach's closing reflection becomes the *draft* of
   * the session memory — shown on the end card for review, revisable via the
   * AI, and saved only when the user confirms. `closingText` is passed by the
   * wrap-up turn (whose closure has the freshest reply); the manual path
   * falls back to the last coach message in the transcript.
   */
  const finishGrow = (closingText?: string) => {
    if (endedRef.current) return;
    endedRef.current = true;
    const lastCoach =
      closingText ??
      [...gmsgs]
        .reverse()
        .find((m) => m.role === "assistant" && m.content.trim() && !m.error)
        ?.content ??
      null;
    setMemoryDraft(lastCoach);
    // The decision now exists — make it survive reloads until the user chooses.
    // The live-session cache has served its purpose and yields to the pending-end card.
    if (lastCoach) savePendingEnd(context.goalId, lastCoach);
    clearGrowSession(context.goalId);
    setMode("grow-closing");
    setTimeout(() => {
      setGmsgs((p) => [...p, { id: uid(), role: "end", content: "" }]);
      setMode("grow-end");
    }, 800);
  };

  /**
   * "Edit by telling the AI": rewrites the memory draft per the user's
   * instruction. A standalone request — it never touches the transcript;
   * only the preview on the end card updates.
   */
  const reviseMemory = (instruction: string) => {
    if (!memoryDraft || memoryRevising) return;
    setMemoryRevising(true);
    let revised = "";
    streamChat({
      goalId: context.goalId,
      message:
        "[The user wants to adjust the session summary that is about to be saved as " +
        "session memory. Apply their request and reply with ONLY the revised summary " +
        "text in the same language as the current summary — no preamble, no quotes, " +
        "and do not call any tools.\nUser request: " +
        instruction +
        "\nCurrent summary:\n" +
        memoryDraft +
        "]",
      history: [],
      provider: activeProv,
      sessionType: "chat",
      onToken: (tok) => {
        revised += tok;
      },
      onDone: () => {
        if (revised.trim()) {
          setMemoryDraft(revised.trim());
          savePendingEnd(context.goalId, revised.trim());
        }
        setMemoryRevising(false);
      },
      onError: (err) => {
        setMemoryRevising(false);
        toast.error(
          err === "NO_KEY" ? "No API key configured." : err || "AI error.",
        );
      },
    });
  };

  /**
   * Timer-driven close. Instead of cutting the conversation off, ask the coach
   * for a proper goodbye (the instruction itself is never shown); the end card
   * appears only after that reply lands. English instruction — the prompt's
   * language rule makes the coach answer in the user's language.
   */
  const WRAP_UP_INSTRUCTION =
    "[The session timer has run out. Close the session now: in the language we have " +
    "been speaking, briefly reflect the key insights of this conversation and confirm any " +
    "commitments or next steps I named. For EACH commitment or next step I voiced, call " +
    "the propose_goal_change tool (e.g. kind='target' or 'action') so I can accept it " +
    "into my goal — do this in this same reply. Then say a warm goodbye. " +
    "Do not ask a new question.]";
  const sendGrowRef = useRef(sendGrow);
  useEffect(() => {
    sendGrowRef.current = sendGrow;
  });

  // Proposals from this session that still await a decision — accepted and
  // rejected ones must not be counted, or the wrap-up claims phantom work.
  const sessionProposals = gmsgs.reduce(
    (n, m) =>
      n + (m.proposals?.filter((pr) => pr.status === "pending").length ?? 0),
    0,
  );

  const closeSession = (save: boolean) => {
    // "Save memory" persists exactly what the end card previewed (the coach's
    // closing reflection, possibly revised by the user via the AI).
    let saved = false;
    if (save && context.goalId && memoryDraft?.trim()) {
      saved = true;
      saveSessionMemory(context.goalId, memoryDraft).catch(() =>
        toast.error("Couldn't save the session memory — it won't carry over."),
      );
    }
    setMode("chat");
    setSession(null);
    setMemoryDraft(null);
    clearPendingEnd(context.goalId); // the user decided — the card may rest
    clearGrowSession(context.goalId);
    const note = save
      ? (saved
          ? "Session memory saved."
          : "Session ended — nothing to save yet.") +
        (sessionProposals > 0
          ? ` ${sessionProposals} proposal${sessionProposals === 1 ? "" : "s"} from the session await your review.`
          : "")
      : "Session ended without saving memory.";
    setMsgs((p) => [...p, { id: uid(), role: "system", content: note }]);
  };

  // ── Live session persistence & resume ─────────────────────────────────────

  // Keep the cached session fresh: settled transcript + recomputed end moment.
  // Skipped while streaming (one write per turn, not per token).
  useEffect(() => {
    if (!session || busy || endedRef.current) return;
    if (mode !== "grow-active" && mode !== "grow-closing") return;
    saveGrowSession(context.goalId, {
      mins: session.mins,
      total: session.total,
      endsAt: Date.now() + session.remaining * 1000,
      msgs: gmsgs.filter((m) => !m.streaming && m.role !== "end"),
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gmsgs, busy, mode]);

  // Resume an interrupted session on mount / goal switch. If its time ran out
  // while the tab was closed, the restored zero on the clock triggers the
  // normal wrap-up → end-card flow instead of losing the result.
  useEffect(() => {
    if (inGrow) return;
    if (loadPendingEnd(context.goalId)) return; // an undecided end card wins
    const stored = loadGrowSession(context.goalId);
    if (!stored) return;
    const remaining = Math.max(
      0,
      Math.round((stored.endsAt - Date.now()) / 1000),
    );
    const hasContent = stored.msgs.some(
      (m) => m.role === "assistant" && m.content.trim() && !m.error,
    );
    if (remaining <= 0 && !hasContent) {
      // Expired with nothing said — nothing worth closing ceremonially.
      clearGrowSession(context.goalId);
      return;
    }
    setGmsgs(stored.msgs);
    endedRef.current = false;
    wrapUpRef.current = false;
    setSession({ total: stored.total, remaining, mins: stored.mins });
    setMode("grow-active");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scopeKey]);

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
    // Time's up: request the coach's closing reply (once, and only between
    // turns — a streaming reply finishes first; busy flipping re-runs this).
    if (
      session.remaining <= 0 &&
      !endedRef.current &&
      !wrapUpRef.current &&
      !busy
    ) {
      wrapUpRef.current = true;
      sendGrowRef.current(WRAP_UP_INSTRUCTION, { wrapUp: true });
    }
  }, [session, mode, busy, WRAP_UP_INSTRUCTION]);

  // ── Provider sheet callbacks ──────────────────────────────────────────────

  const handleSaveKey = async (provId: string, raw: string) => {
    const hint =
      raw.length > 8
        ? `${raw.slice(0, 6)}••••••••${raw.slice(-4)}`
        : `${raw.slice(0, 2)}••••`;
    try {
      await saveApiKey(provId, raw);
      setProviders((ps) =>
        ps.map((p) =>
          p.id === provId ? { ...p, connected: true, keyHint: hint } : p,
        ),
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

  const activeProvider =
    providers.find((p) => p.id === activeProv) || providers[0];
  const activeLabel = activeProvider.connected
    ? activeProvider.activeModel
    : "No key";

  // The pending card (rendered in the footer). All callbacks are bound to its message.
  const proposalGroupFor = (m: Msg) => (
    <ProposalGroup
      proposals={m.proposals ?? []}
      goal={goal}
      onApprove={(pp) => {
        // Opening a goal because the change can't be made here: carry the user's original
        // request into that goal's chat so a card appears on arrival, not an empty chat.
        if (pp.kind === "open_goal" && pp.goalId) {
          const list = inGrow ? gmsgs : msgs;
          const i = list.findIndex((x) => x.id === m.id);
          const userMsg =
            i >= 0
              ? [...list.slice(0, i)].reverse().find((x) => x.role === "user")
              : undefined;
          const instr = pp.followup || userMsg?.content;
          if (instr) stashHandoff(pp.goalId, instr);
        }
        applyProposal(pp);
        if (RESOURCE_CREATE_KINDS.has(pp.kind) && context.goalId) {
          const gid = context.goalId;
          (inGrow ? setGmsgs : setMsgs)((msgs) =>
            msgs.map((msg) =>
              msg.id === m.id
                ? {
                    ...msg,
                    proposals: msg.proposals?.map((pr) =>
                      pr.id === pp.id
                        ? {
                            ...pr,
                            createdRef: {
                              kind: "resource" as const,
                              goalId: gid,
                            },
                          }
                        : pr,
                    ),
                  }
                : msg,
            ),
          );
        }
      }}
      onOpenCreated={onOpenCreated}
      onCreateProposal={(pp) =>
        onCreateProposal(pp, (ref) => {
          (inGrow ? setGmsgs : setMsgs)((msgs) =>
            msgs.map((msg) =>
              msg.id === m.id
                ? {
                    ...msg,
                    proposals: msg.proposals?.map((pr) =>
                      pr.id === pp.id ? { ...pr, createdRef: ref } : pr,
                    ),
                  }
                : msg,
            ),
          );
        })
      }
      onResolveOne={(proposalId, status) => {
        (inGrow ? setGmsgs : setMsgs)((msgs) =>
          msgs.map((msg) =>
            msg.id === m.id
              ? {
                  ...msg,
                  proposals: msg.proposals?.map((pr) =>
                    pr.id === proposalId ? { ...pr, status } : pr,
                  ),
                }
              : msg,
          ),
        );
        const pr = m.proposals?.find((x) => x.id === proposalId);
        if (pr?.serverId != null) {
          (status === "approved" ? approveProposal : rejectProposal)(
            pr.serverId,
          ).catch(() => {});
        }
      }}
      onExpand={setContentModal}
      onInstructOne={(p, instruction) =>
        reviseInPlace(m.id, p.id, p, instruction)
      }
    />
  );

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
              <TimerPill
                frac={timerFrac}
                closing={closing}
                label={timerLabel}
              />
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
                <button
                  onClick={newChat}
                  disabled={busy}
                  className="inline-flex items-center gap-1.5 px-2.5 h-[34px] rounded-[9px] text-white/74 text-[12.5px] font-medium hover:bg-white/12 hover:text-white disabled:opacity-40 transition-colors"
                  title="Start a new chat — clears the history so context uses only this goal's data"
                  aria-label="New chat"
                >
                  <Ic path={PATHS.plus} size={13} /> New chat
                </button>
              )}
              <button
                onClick={onClose}
                className="w-[34px] h-[34px] grid place-items-center rounded-[9px] text-white/74 hover:bg-white/12 hover:text-white transition-colors"
                aria-label="Close"
              >
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
            <span
              className={cn(
                "w-[7px] h-[7px] rounded-full",
                activeProvider.connected
                  ? "bg-[#5fd0a8] shadow-[0_0_0_3px_rgba(95,208,168,0.2)]"
                  : "bg-[#d99a4e] shadow-[0_0_0_3px_rgba(217,154,78,0.2)]",
              )}
            />
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
      <div
        ref={scrollRef}
        data-vaul-no-drag
        className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden px-4 py-2 flex flex-col gap-4 scrollbar-thin scrollbar-thumb-white/20"
      >
        {/* Empty state */}
        {!inGrow && msgs.length === 0 && (
          <div className="pt-5 pb-2 text-center">
            <div className="w-[52px] h-[52px] rounded-full bg-white/10 border border-white/20 grid place-items-center mx-auto mb-4">
              <Ic path={PATHS.leaf} size={20} />
            </div>
            <p className="text-[14px] leading-[1.6] text-white/74 max-w-[30ch] mx-auto mb-5">
              {goal
                ? `I'm here to help with "${goal.title}". Ask anything or start a GROW session.`
                : "I'm here to help you think. Ask me anything, or just say what you want to achieve and I'll help you create a new goal."}
            </p>
            <div className="flex flex-col gap-2">
              {(goal ? buildGoalSuggestions(goal) : SUGGESTIONS_GLOBAL).map(
                (s) => (
                  <button
                    key={s.id}
                    onClick={() => sendChat(s.text)}
                    className="flex items-center gap-2.5 px-3.5 py-3 rounded-xl bg-white/10 border border-white/20 text-white text-[13.5px] text-left hover:border-white/35 hover:-translate-y-px transition-all"
                  >
                    <Ic
                      path={PATHS[s.icon as IconKey] ?? PATHS.sparkles}
                      size={15}
                      className="shrink-0 text-white/80"
                    />
                    <span className="flex-1">{s.text}</span>
                  </button>
                ),
              )}
            </div>
          </div>
        )}

        {/* Messages */}
        {list.map((m) => {
          if (m.role === "user") {
            return (
              <div key={m.id} className="group flex flex-col items-end gap-1">
                <div className="max-w-[86%] min-w-0 px-3.5 py-2.5 rounded-2xl rounded-br-sm bg-white text-[#083f3a] text-[14px] leading-[1.5] whitespace-pre-wrap break-words [overflow-wrap:anywhere] select-text selection:bg-[#006d67]/25 selection:text-[#083f3a]">
                  {m.content}
                </div>
                <CopyButton text={m.content} />
              </div>
            );
          }
          if (m.role === "system") {
            return (
              <div
                key={m.id}
                className="flex items-center gap-2 self-center text-[12.5px] text-white/74 bg-white/10 px-3 py-1.5 rounded-full"
              >
                <Ic path={PATHS.check} size={12} /> {m.content}
              </div>
            );
          }
          if (m.role === "end") {
            return (
              <GrowEndCard
                key={m.id}
                proposals={sessionProposals}
                memory={memoryDraft}
                revising={memoryRevising}
                onRevise={reviseMemory}
                onSave={() => closeSession(true)}
                onDiscard={() => closeSession(false)}
              />
            );
          }
          // assistant — error bubbles render with a warning icon (never an emoji)
          if (m.error) {
            return (
              <div
                key={m.id}
                className="flex items-start gap-2 text-[14px] leading-[1.55] text-[#f0b860] max-w-[94%] min-w-0 break-words [overflow-wrap:anywhere]"
              >
                <Ic
                  path={PATHS.alert}
                  size={15}
                  className="shrink-0 mt-[3px]"
                />
                <span className="select-text">{m.content}</span>
              </div>
            );
          }
          return (
            <div key={m.id} className="group flex flex-col gap-2.5">
              <div className="text-[14.5px] leading-[1.62] text-white max-w-[94%] min-w-0 break-words [overflow-wrap:anywhere] select-text selection:bg-white/30 selection:text-white">
                {m.streaming && m.status && !m.content && (
                  <p className="text-[12.5px] italic text-white/60 mb-1">
                    {m.status}
                  </p>
                )}
                <Markdown text={m.content} />
                {m.streaming && (
                  <span className="inline-block w-[7px] h-[15px] ml-0.5 align-text-bottom bg-white rounded-sm animate-pulse" />
                )}
              </div>
              {!m.streaming && m.content && <CopyButton text={m.content} />}
              {/* A pending card lives in the footer (the card is the input). Once it's
                  resolved we only keep a compact result line here, not the full card. */}
              {!m.streaming &&
                m.proposals &&
                m.proposals.length > 0 &&
                !m.proposals.some((pr) => pr.status === "pending") && (
                  <ResultSummary
                    proposals={m.proposals}
                    goal={goal}
                    onOpen={onOpenCreated}
                  />
                )}
              {!m.streaming &&
                m.toolProposals?.map((tp) => (
                  <ToolProposalCard
                    key={tp.id}
                    proposal={tp}
                    onResolve={(status) =>
                      setMsgs((ps) =>
                        ps.map((mm) =>
                          mm.id === m.id
                            ? {
                                ...mm,
                                toolProposals: mm.toolProposals?.map((x) =>
                                  x.id === tp.id ? { ...x, status } : x,
                                ),
                              }
                            : mm,
                        ),
                      )
                    }
                  />
                ))}
              {!m.streaming &&
                m.toolDataProposals?.map((tp) => (
                  <ToolDataProposalCard
                    key={tp.id}
                    proposal={tp}
                    onResolve={(status) =>
                      setMsgs((ps) =>
                        ps.map((mm) =>
                          mm.id === m.id
                            ? {
                                ...mm,
                                toolDataProposals: mm.toolDataProposals?.map(
                                  (x) =>
                                    x.id === tp.id ? { ...x, status } : x,
                                ),
                              }
                            : mm,
                        ),
                      )
                    }
                  />
                ))}
            </div>
          );
        })}

        {/* An undecided session end (restored after a reload): the card stays
            until the user explicitly saves or discards — never a silent loss. */}
        {!inGrow && memoryDraft && (
          <GrowEndCard
            proposals={0}
            memory={memoryDraft}
            revising={memoryRevising}
            onRevise={reviseMemory}
            onSave={() => closeSession(true)}
            onDiscard={() => closeSession(false)}
          />
        )}
      </div>

      {/* Footer. While revising a card, show a cancellable "Revising…" state; otherwise a
          pending card renders here (the card IS the input); otherwise the composer. */}
      {mode !== "grow-end" && revising && (
        <div className="px-3 pb-3 pt-1 shrink-0">
          <div className="flex items-center gap-2.5 rounded-[14px] border border-white/20 bg-white px-4 py-3 text-[#083f3a] shadow-[0_6px_20px_-14px_rgba(0,0,0,0.4)]">
            <span className="h-4 w-4 shrink-0 rounded-full border-2 border-[#006d67]/30 border-t-[#006d67] animate-spin" />
            <span className="flex-1 min-w-0 text-[13.5px] truncate">
              Revising «{revising.label}»…
            </span>
            <button
              onClick={cancelRevise}
              className="shrink-0 text-[13px] font-medium text-[#083f3a]/55 hover:text-red-600 transition-colors px-1.5 py-1"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
      {/* Note: shown in grow-end too — the wrap-up turn may propose capturing
          the user's commitments, and those cards must stay actionable. */}
      {!revising && pendingMsg && (
        <div className="px-3 pb-3 pt-1 shrink-0">
          {proposalGroupFor(pendingMsg)}
        </div>
      )}
      {mode !== "grow-end" && !revising && !pendingMsg && (
        <>
          {inGrow && (
            <div className="px-4 pb-1">
              <button
                onClick={() => setConfirmEnd(true)}
                className="text-white/60 text-[12.5px] underline underline-offset-2 hover:text-white transition-colors"
              >
                End session early
              </button>
            </div>
          )}
          <Composer
            onSend={inGrow ? sendGrow : sendChat}
            placeholder={
              inGrow
                ? "Answer in your own words…"
                : "Ask, plan, or request an action…"
            }
            busy={busy}
            onStop={stopStream}
            initialValue={draftRef.current}
            onDraftChange={(v) => {
              draftRef.current = v;
            }}
            leftAction={
              !inGrow && goal ? (
                <button
                  onClick={() => setMode("grow-start")}
                  className="inline-flex items-center gap-1.5 px-2 h-8 shrink-0 rounded-lg text-[#006d67] text-[13px] font-medium hover:bg-[#006d67]/10 transition-colors"
                >
                  <Ic path={PATHS.leaf} size={14} /> Start GROW session
                </button>
              ) : undefined
            }
          />
        </>
      )}

      {/* Overlays */}
      {mode === "grow-start" && (
        <GrowStartOverlay
          onStart={startGrow}
          onCancel={() => setMode("chat")}
        />
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
          onConfirm={() => {
            setConfirmEnd(false);
            finishGrow();
          }}
          onCancel={() => setConfirmEnd(false)}
        />
      )}

      {/* AI-initiated deletion: the AI only opens this dialog — the user decides. */}
      {pendingDelete &&
        (() => {
          const isGoal = pendingDelete.kind === "goal";
          const g = goals.find(
            (x) => x.id === (isGoal ? pendingDelete.id : pendingDelete.goalId),
          );
          const targetTitle = !isGoal
            ? g?.targets.find((t) => t.id === pendingDelete.id)?.title
            : undefined;
          const name = isGoal
            ? (g?.title ?? "this goal")
            : (targetTitle ?? "this target");
          return (
            <ConfirmDialog
              open
              onOpenChange={(o) => {
                if (!o) setPendingDelete(null);
              }}
              title={isGoal ? "Delete this goal?" : "Delete this target?"}
              description={
                isGoal
                  ? `“${name}” and all its targets, options, notes and history will be permanently deleted. This can't be undone.`
                  : `“${name}” will be permanently removed from this goal. This can't be undone.`
              }
              confirmLabel="Yes, delete"
              onConfirm={() => {
                if (isGoal) {
                  deleteGoal(pendingDelete.id);
                  if (context.goalId === pendingDelete.id)
                    navigate({ to: "/" });
                  toast.success("Goal deleted");
                } else if (pendingDelete.goalId) {
                  removeTarget(pendingDelete.goalId, pendingDelete.id);
                  toast.success("Target deleted");
                }
                setPendingDelete(null);
              }}
            />
          );
        })()}

      {/* Full proposal content (long note / goal description) the card can't fit. */}
      {contentModal && (
        <ContentModal
          title={contentModal.title}
          body={contentModal.body}
          html={contentModal.html}
          onClose={() => setContentModal(null)}
        />
      )}
    </div>
  );
}

// ── Proposal content modal ───────────────────────────────────────────────────

function ContentModal({
  title,
  body,
  html,
  onClose,
}: {
  title: string;
  body: string;
  html?: boolean;
  onClose: () => void;
}) {
  // Close on Escape, like the other overlays.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div
      className="absolute inset-0 z-50 flex items-center justify-center p-4 bg-[rgba(8,40,38,0.45)] backdrop-blur-[2px]"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-[440px] max-h-[80%] flex flex-col bg-white text-[#083f3a] rounded-[18px] shadow-[0_20px_60px_-20px_rgba(0,0,0,0.55)]"
        style={{ animation: "slideUp 0.25s cubic-bezier(0.2,0.8,0.2,1) both" }}
      >
        <div className="flex items-start gap-3 px-5 pt-4 pb-3 border-b border-[#eef1f0]">
          <h3 className="font-['Playfair_Display'] text-[18px] font-semibold leading-[1.25] flex-1 break-words [overflow-wrap:anywhere]">
            {title}
          </h3>
          <button
            onClick={onClose}
            aria-label="Close"
            className="shrink-0 text-[#083f3a]/40 hover:text-[#083f3a] transition-colors p-1 -mr-1"
          >
            <X size={18} />
          </button>
        </div>
        <div className="px-5 py-4 overflow-y-auto text-[14px] leading-[1.6] text-[#083f3a]/85 [overflow-wrap:anywhere]">
          {/* Notes are stored as HTML (TipTap) — render them formatted, like the app's
              note view; goal descriptions are plain text → Markdown. */}
          {html ? (
            <div
              className="tiptap-content prose prose-sm max-w-none"
              dangerouslySetInnerHTML={{ __html: body }}
            />
          ) : (
            <Markdown text={body} />
          )}
        </div>
      </div>
    </div>
  );
}

// ── Icon system ────────────────────────────────────────────────────────────

const PATHS = {
  leaf: '<path d="M7 20h10"/><path d="M10 20c5.5-2.5.8-6.4 3-10"/><path d="M9.5 9.4c1.1.8 1.8 2.2 2.3 3.7-2 .4-3.5.4-4.8-.3-1.2-.6-2.3-1.9-3-4.2 2.8-.5 4.4 0 5.5.8z"/><path d="M14.1 6a7 7 0 0 0-1.1 4c1.9-.1 3.3-.6 4.3-1.4 1-1 1.6-2.3 1.7-4.6-2.7.1-4 1-4.9 2z"/>',
  key: '<path d="m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L21 5"/><path d="m21 2-9.6 9.6"/><circle cx="7.5" cy="15.5" r="5.5"/>',
  chevron: '<path d="m6 9 6 6 6-6"/>',
  clock: '<path d="M12 6v6l4 2"/><circle cx="12" cy="12" r="10"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  x: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
  sparkles:
    '<path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .962 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.962 0z"/>',
  brain:
    '<path d="M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z"/><path d="M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z"/>',
  shield:
    '<path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/>',
  plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
  switch_:
    '<path d="M8 3 4 7l4 4"/><path d="M4 7h16"/><path d="m16 21 4-4-4-4"/><path d="M20 17H4"/>',
  pencil:
    '<path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"/>',
  target:
    '<circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/>',
  copy: '<rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/>',
  expand:
    '<path d="M15 3h6v6"/><path d="M9 21H3v-6"/><path d="M21 3l-7 7"/><path d="M3 21l7-7"/>',
  zap: '<path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"/>',
  trending: '<path d="M16 7h6v6"/><path d="m22 7-8.5 8.5-5-5L2 17"/>',
  compass:
    '<path d="m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z"/><circle cx="12" cy="12" r="10"/>',
  alert:
    '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
  trophy:
    '<path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22"/><path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22"/><path d="M18 2H6v7a6 6 0 0 0 12 0V2Z"/>',
  trash:
    '<path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/><line x1="10" x2="10" y1="11" y2="17"/><line x1="14" x2="14" y1="11" y2="17"/>',
};

/** Maps a suggestion's icon key to a lucide path (we use lucide icons everywhere — never
 *  emoji). Falls back to the sparkles icon if a key is ever unmapped. */
type IconKey = keyof typeof PATHS;

function Ic({
  path,
  size,
  className,
}: {
  path: string;
  size: number;
  className?: string;
}) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
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
        strong: ({ children }) => (
          <strong className="font-semibold">{children}</strong>
        ),
        em: ({ children }) => <em className="italic">{children}</em>,
        ul: ({ children }) => (
          <ul className="list-disc pl-5 mb-2 space-y-0.5">{children}</ul>
        ),
        ol: ({ children }) => (
          <ol className="list-decimal pl-5 mb-2 space-y-0.5">{children}</ol>
        ),
        li: ({ children }) => <li>{children}</li>,
        h1: ({ children }) => (
          <div className="font-['Playfair_Display'] text-[19px] font-semibold mb-1.5 mt-3">
            {children}
          </div>
        ),
        h2: ({ children }) => (
          <div className="font-['Playfair_Display'] text-[16.5px] font-semibold mb-1.5 mt-3">
            {children}
          </div>
        ),
        h3: ({ children }) => (
          <div className="font-['Playfair_Display'] text-[15px] font-semibold mb-1.5 mt-2">
            {children}
          </div>
        ),
        h4: ({ children }) => (
          <div className="font-['Playfair_Display'] text-[14px] font-semibold mb-1 mt-2">
            {children}
          </div>
        ),
        code: ({ children, className }) => {
          const isBlock = className?.includes("language-");
          return isBlock ? (
            <pre className="bg-white/10 rounded-lg px-3 py-2 overflow-x-auto text-[13px] font-mono mb-2">
              <code>{children}</code>
            </pre>
          ) : (
            <code className="bg-white/15 rounded px-1 py-0.5 text-[13px] font-mono">
              {children}
            </code>
          );
        },
        blockquote: ({ children }) => (
          <blockquote className="border-l-2 border-white/40 pl-3 my-2 text-white/80">
            {children}
          </blockquote>
        ),
        a: ({ href, children }) => (
          <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            className="underline underline-offset-2 text-white/90 hover:text-white"
          >
            {children}
          </a>
        ),
        hr: () => <hr className="border-white/20 my-3" />,
      }}
    >
      {text}
    </ReactMarkdown>
  );
}

// ── Timer pill ─────────────────────────────────────────────────────────────

function TimerPill({
  frac,
  closing,
  label,
}: {
  frac: number;
  closing: boolean;
  label: string;
}) {
  // Display-only: this used to be a "Skip (demo)" button that silently jumped
  // the session to its closing stretch — a stray click made the time feel fake.
  return (
    <span
      className={cn(
        "inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/12 text-white font-sans",
        closing && "text-[#f0b860]",
      )}
    >
      <Ic path={PATHS.clock} size={12} />
      <span
        className={cn(
          "text-[12.5px] font-semibold tabular-nums tracking-[0.02em]",
          closing && "text-[#f0b860]",
        )}
      >
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
    </span>
  );
}

// ── Proposal card ──────────────────────────────────────────────────────────

const KIND_META: Record<string, { icon: string; label: string }> = {
  new_goal: { icon: PATHS.sparkles, label: "New goal" },
  target: { icon: PATHS.target, label: "New target" },
  task: { icon: PATHS.check, label: "New task" },
  option: { icon: PATHS.sparkles, label: "Strategy option" },
  note: { icon: PATHS.pencil, label: "Resource note" },
  link: { icon: PATHS.sparkles, label: "New link" },
  email: { icon: PATHS.sparkles, label: "New contact" },
  edit: { icon: PATHS.pencil, label: "Goal edit" },
  obstacle: { icon: PATHS.shield, label: "New obstacle" },
  action: { icon: PATHS.leaf, label: "Current action" },
  confidence: { icon: PATHS.brain, label: "Confidence" },
  deadline: { icon: PATHS.clock, label: "Deadline" },
  edit_target: { icon: PATHS.target, label: "Edit target" },
  edit_option: { icon: PATHS.sparkles, label: "Edit option" },
  edit_obstacle: { icon: PATHS.shield, label: "Edit obstacle" },
  edit_action: { icon: PATHS.leaf, label: "Edit action" },
  edit_note: { icon: PATHS.pencil, label: "Edit note" },
  edit_link: { icon: PATHS.pencil, label: "Edit link" },
  edit_email: { icon: PATHS.pencil, label: "Edit contact" },
  complete_target: { icon: PATHS.check, label: "Target status" },
  target_progress: { icon: PATHS.target, label: "Target progress" },
  select_option: { icon: PATHS.sparkles, label: "Select option" },
  checklist_item: { icon: PATHS.check, label: "Checklist item" },
  add_checklist_item: { icon: PATHS.plus, label: "New sub-task" },
  edit_goal: { icon: PATHS.pencil, label: "Edit goal" },
  open_goal: { icon: PATHS.switch_, label: "Open goal" },
  delete_goal: { icon: PATHS.trash, label: "Delete goal" },
  delete_target: { icon: PATHS.trash, label: "Delete target" },
  delete_option: { icon: PATHS.trash, label: "Delete option" },
  delete_obstacle: { icon: PATHS.trash, label: "Delete obstacle" },
  delete_action: { icon: PATHS.trash, label: "Delete action" },
  delete_checklist_item: { icon: PATHS.trash, label: "Delete sub-task" },
};

const PROPOSAL_INPUT_CLS =
  "w-full border border-[#d9dddc] rounded-lg px-3 py-2 text-[14px] text-[#083f3a] bg-white outline-none focus:border-[#006d67] focus:ring-2 focus:ring-[#006d67]/12 transition";

// Truncate long strings for one-line card display (full text lives behind the
// "Read full content" modal).
const truncate = (s: string, n = 64) =>
  s.length > n ? s.slice(0, n - 1) + "…" : s;

/**
 * Turns a Proposal into the exact text shown on its card — the point of the card
 * is that the user knows precisely what will be saved. For kinds that reference
 * an existing item (select an option, complete a target, edit a checklist item…)
 * we resolve the id against the live goal so the card names the actual item
 * ("Select «Find a mentor»") instead of a vague label ("Select this option").
 * `body` is the long content (note text / goal description) shown in a modal.
 */
function proposalDisplay(
  p: Proposal,
  goal?: Goal,
): { headline: string; detail?: string; body?: string } {
  let headline = p.title;
  let detail = p.detail;
  const body =
    p.kind === "note" || p.kind === "edit_note" || p.kind === "new_goal"
      ? p.body
      : undefined;

  const targetOf = (id?: string) => goal?.targets.find((t) => t.id === id);

  switch (p.kind) {
    // (option's "make it active" is shown as its own checkbox — don't repeat it in the detail)
    case "select_option": {
      const opt = goal?.options.find((o) => o.id === p.itemId);
      if (opt) {
        headline = `Select «${truncate(opt.text, 48)}»`;
        detail = "Make this the chosen strategy";
      }
      break;
    }
    case "complete_target": {
      const t = targetOf(p.itemId)?.title;
      if (t) {
        headline = `${p.done === false ? "Reopen" : "Complete"} «${truncate(t, 48)}»`;
        detail = "Target status";
      }
      break;
    }
    case "target_progress": {
      const t = targetOf(p.itemId)?.title;
      const val = (p.rawValue ?? p.title).replace(/^Progress → /, "");
      if (t) {
        headline = `«${truncate(t, 40)}» → ${val}`;
        detail = "Update progress";
      }
      break;
    }
    case "checklist_item": {
      let itemText: string | undefined;
      let parentTitle: string | undefined;
      goal?.targets.forEach((t) => {
        if (t.type === "checklist") {
          const it = t.items.find((i) => i.id === p.itemId);
          if (it) {
            itemText = it.text;
            parentTitle = t.title;
          }
        }
      });
      if (itemText) {
        const newText = p.rawValue;
        headline = newText
          ? `“${truncate(itemText, 32)}” → “${truncate(newText, 32)}”`
          : p.done === true
            ? `Check “${truncate(itemText, 48)}”`
            : p.done === false
              ? `Uncheck “${truncate(itemText, 48)}”`
              : `Update “${truncate(itemText, 48)}”`;
        detail = parentTitle
          ? `in ${truncate(parentTitle, 40)}`
          : "Checklist item";
      }
      break;
    }
    case "edit_target": {
      const t = targetOf(p.itemId)?.title;
      if (t)
        detail = `was «${truncate(t, 40)}»${p.deadline ? ` · due ${p.deadline}` : ""}`;
      break;
    }
    case "edit_option": {
      const old = goal?.options.find((o) => o.id === p.itemId)?.text;
      if (old) detail = `was «${truncate(old, 48)}»`;
      break;
    }
    case "edit_obstacle":
    case "edit_action": {
      const list =
        p.kind === "edit_obstacle"
          ? goal?.reality.obstacles
          : goal?.reality.actions;
      const old = list?.find((i) => i.id === p.itemId)?.text;
      if (old) detail = `was «${truncate(old, 48)}»`;
      break;
    }
    // Goal-level ops from the All-Goals chat: lead with WHICH goal, then the change.
    case "edit_goal": {
      headline = p.goalTitle ? `«${truncate(p.goalTitle, 48)}»` : "This goal";
      detail =
        p.field === "confidence"
          ? `Confidence → ${(p.rawValue ?? "").replace(/\D/g, "")}/10`
          : p.field === "deadline"
            ? `Deadline → ${fmtDeadline(p.rawValue)}`
            : `Rename → «${truncate(p.rawValue ?? p.title, 40)}»`;
      break;
    }
    case "open_goal": {
      const name = p.goalTitle ? `«${truncate(p.goalTitle, 48)}»` : "this goal";
      headline = `Open ${name}`;
      detail = p.openSubject
        ? `You can't edit ${p.openSubject} from the goals overview — open ${name} to continue.`
        : `Open ${name} to work inside it.`;
      break;
    }
    case "delete_option": {
      const t = goal?.options.find((o) => o.id === p.itemId)?.text;
      headline = t ? `Delete «${truncate(t, 48)}»` : "Delete this option";
      detail = "This strategy option will be removed.";
      break;
    }
    case "delete_obstacle":
    case "delete_action": {
      const list =
        p.kind === "delete_obstacle"
          ? goal?.reality.obstacles
          : goal?.reality.actions;
      const t = list?.find((i) => i.id === p.itemId)?.text;
      const noun = p.kind === "delete_obstacle" ? "obstacle" : "action";
      headline = t ? `Delete «${truncate(t, 48)}»` : `Delete this ${noun}`;
      detail = `This ${noun} will be removed.`;
      break;
    }
    case "delete_checklist_item": {
      let itemText: string | undefined;
      goal?.targets.forEach((t) => {
        if (t.type === "checklist") {
          const it = t.items.find((i) => i.id === p.itemId);
          if (it) itemText = it.text;
        }
      });
      headline = itemText
        ? `Delete “${truncate(itemText, 48)}”`
        : "Delete this checklist item";
      detail = "This sub-task will be removed.";
      break;
    }
  }
  return { headline, detail, body };
}

/** Shared visual body of a proposal: kind chip, headline, detail, an optional
 *  "Read full content" button (opens a modal). */
function ProposalBody({
  p,
  goal,
  onExpand,
  compact,
}: {
  p: Proposal;
  goal?: Goal;
  onExpand: (content: { title: string; body: string; html?: boolean }) => void;
  compact?: boolean;
}) {
  const meta = KIND_META[p.kind] || KIND_META.target;
  const { headline, detail, body } = proposalDisplay(p, goal);
  // The kind badge already names the action — never repeat it in the detail line (e.g.
  // badge "Current action" + detail "Current action"). Only show detail when it adds info.
  const showDetail =
    !!detail && detail.trim().toLowerCase() !== meta.label.trim().toLowerCase();

  return (
    <>
      <div className="mb-2">
        <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#006d67]">
          <Ic path={meta.icon} size={12} className="text-[#006d67]" />
          {meta.label}
        </span>
      </div>
      <div
        className={cn(
          "font-['Playfair_Display'] font-semibold leading-[1.25]",
          compact ? "text-[16px]" : "text-[17px]",
        )}
      >
        {headline}
      </div>
      {showDetail && (
        <p className="mt-1.5 text-[13.5px] leading-[1.5] text-[#083f3a]/60 break-words [overflow-wrap:anywhere]">
          {detail}
        </p>
      )}

      {body && body.trim() && (
        <button
          onClick={() =>
            onExpand({
              title: headline,
              body,
              html: p.kind === "note" || p.kind === "edit_note",
            })
          }
          className="inline-flex items-center gap-1.5 mt-2.5 text-[12.5px] font-medium text-[#006d67] hover:text-[#005b56] transition-colors"
        >
          <Ic path={PATHS.expand} size={13} /> Read full content
        </button>
      )}
    </>
  );
}

/** Inline "tell the AI how to change this" editor, shared by single + stepped cards. */
function InstructBox({
  headline,
  onSend,
  onCancel,
}: {
  headline: string;
  onSend: (instruction: string) => void;
  onCancel: () => void;
}) {
  const [instruction, setInstruction] = useState("");
  const taRef = useRef<HTMLTextAreaElement>(null);
  const send = () => {
    const t = instruction.trim();
    if (t) onSend(t);
  };
  // The textarea autofocuses → keyboard opens; scroll it into view so it isn't
  // hidden behind the keyboard (the chat panel doesn't reposition it on its own).
  useEffect(() => {
    const t = setTimeout(
      () =>
        taRef.current?.scrollIntoView({ block: "center", behavior: "smooth" }),
      320,
    );
    return () => clearTimeout(t);
  }, []);
  return (
    <div className="flex flex-col gap-2">
      <div className="font-['Playfair_Display'] text-[15px] font-semibold leading-[1.25]">
        {headline}
      </div>
      <p className="text-[12px] text-[#083f3a]/55">
        Tell the AI how to change this — it will re-propose.
      </p>
      <textarea
        ref={taRef}
        value={instruction}
        onChange={(e) => setInstruction(e.target.value)}
        rows={2}
        autoFocus
        placeholder="e.g. “in English”, “make it shorter”, “due next Friday”"
        className={cn(PROPOSAL_INPUT_CLS, "resize-none")}
        onKeyDown={(e) => {
          if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            send();
          }
        }}
      />
      <div className="mt-1 flex items-center gap-2">
        <button
          onClick={send}
          disabled={!instruction.trim()}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#006d67] text-white text-[13px] font-semibold disabled:opacity-40 hover:bg-[#005b56] transition-colors"
        >
          <Ic path={PATHS.sparkles} size={14} /> Send to AI
        </button>
        <button
          onClick={onCancel}
          className="ml-auto text-[13px] text-[#083f3a]/50 hover:text-[#083f3a] transition-colors px-1.5 py-2"
        >
          Cancel
        </button>
      </div>
    </div>
  );
}

const CARD_CLS =
  "rounded-[14px] border border-white/20 bg-white text-[#083f3a] p-4 shadow-[0_6px_20px_-14px_rgba(0,0,0,0.4)] max-w-full";

/** A single proposed change (the common case): polished card, Accept / Edit / Dismiss. */
function ProposalCard({
  p,
  goal,
  onResolve,
  onApprove,
  onInstruct,
  onExpand,
  onOpen,
}: {
  p: Proposal;
  goal?: Goal;
  onResolve: (status: "approved" | "rejected") => void;
  onApprove: (p: Proposal) => void;
  onInstruct: (instruction: string) => void;
  onExpand: (content: { title: string; body: string; html?: boolean }) => void;
  onOpen: (ref: {
    kind: "goal" | "target" | "resource";
    goalId: string;
  }) => void;
}) {
  const [instructing, setInstructing] = useState(false);
  const settled = p.status !== "pending";
  const { headline } = proposalDisplay(p, goal);

  return (
    <div className={CARD_CLS}>
      {instructing ? (
        <InstructBox
          headline={headline}
          onSend={(t) => {
            setInstructing(false);
            onInstruct(t);
          }}
          onCancel={() => setInstructing(false)}
        />
      ) : (
        <>
          <ProposalBody p={p} goal={goal} onExpand={onExpand} />
          {settled ? (
            <div className="mt-3 flex items-center gap-2 flex-wrap">
              <div
                className={cn(
                  "inline-flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full font-medium",
                  p.status === "approved"
                    ? "bg-[#006d67]/10 text-[#006d67]"
                    : "bg-black/6 text-[#083f3a]/50",
                )}
              >
                <Ic
                  path={p.status === "approved" ? PATHS.check : PATHS.x}
                  size={12}
                />
                {p.status === "approved" ? "Added to goal" : "Dismissed"}
              </div>
              {p.status === "approved" && p.createdRef && (
                <button
                  onClick={() => onOpen(p.createdRef!)}
                  className="ml-auto inline-flex items-center gap-1.5 px-3 py-1.5 rounded-[9px] bg-[#006d67] text-white text-[12.5px] font-semibold hover:bg-[#005b56] transition-colors"
                >
                  <Ic path={PATHS.switch_} size={13} /> Open
                </button>
              )}
            </div>
          ) : (
            <div className="mt-3.5 flex items-center gap-2">
              <button
                onClick={() => {
                  onResolve("approved");
                  onApprove(p);
                }}
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#006d67] text-white text-[13px] font-semibold hover:bg-[#005b56] transition-colors"
              >
                <Ic path={PATHS.check} size={14} /> Accept
              </button>
              <button
                onClick={() => setInstructing(true)}
                title="Ask the AI to change this proposal"
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-[9px] border border-[#d9dddc] text-[#083f3a] text-[13px] font-medium hover:border-[#006d67]/40 transition-colors"
              >
                <Ic path={PATHS.pencil} size={13} /> Edit
              </button>
              <button
                onClick={() => onResolve("rejected")}
                className="ml-auto text-[13px] text-[#083f3a]/50 hover:text-red-600 transition-colors px-1.5 py-2"
              >
                Dismiss
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

/** Custom Spira-styled checkbox — a VISUAL element only (the enclosing row is the
 *  clickable control). Native checkboxes aren't allowed by the design spec, and a
 *  nested <button> inside the row button wouldn't toggle. */
function CheckBox({ checked }: { checked: boolean }) {
  return (
    <span
      aria-hidden
      className={cn(
        "mt-0.5 h-5 w-5 shrink-0 rounded-[6px] border grid place-items-center transition-colors",
        checked
          ? "bg-[#006d67] border-[#006d67] text-white"
          : "border-[#cfd6d4] bg-white text-transparent",
      )}
    >
      <Ic path={PATHS.check} size={12} />
    </span>
  );
}

/**
 * One option that should ALSO be made active → a single card with TWO checkboxes:
 * "Create «X»" and "Make it the active option". Untick "active" to just create it.
 */
function OptionAspectCard({
  p,
  goal,
  onResolve,
  onApprove,
  onInstruct,
}: {
  p: Proposal;
  goal?: Goal;
  onResolve: (status: "approved" | "rejected") => void;
  onApprove: (p: Proposal) => void;
  onInstruct: (instruction: string) => void;
}) {
  const [createOpt, setCreateOpt] = useState(true);
  const [makeActive, setMakeActive] = useState(true);
  const [instructing, setInstructing] = useState(false);
  const settled = p.status !== "pending";
  const { headline } = proposalDisplay(p, goal);

  if (settled) {
    return (
      <div className={CARD_CLS}>
        <div
          className={cn(
            "inline-flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full font-medium",
            p.status === "approved"
              ? "bg-[#006d67]/10 text-[#006d67]"
              : "bg-black/6 text-[#083f3a]/50",
          )}
        >
          <Ic
            path={p.status === "approved" ? PATHS.check : PATHS.x}
            size={12}
          />
          {p.status === "approved" ? "Added to goal" : "Dismissed"}
        </div>
      </div>
    );
  }

  if (instructing) {
    return (
      <div className={CARD_CLS}>
        <InstructBox
          headline={headline}
          onSend={(t) => {
            setInstructing(false);
            onInstruct(t);
          }}
          onCancel={() => setInstructing(false)}
        />
      </div>
    );
  }

  const confirm = () => {
    if (!createOpt) {
      onResolve("rejected");
      return;
    }
    onResolve("approved");
    onApprove({ ...p, done: makeActive });
  };

  return (
    <div className={CARD_CLS}>
      <div className="mb-3">
        <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#006d67]">
          <Ic path={KIND_META.option.icon} size={12} /> Strategy option
        </span>
      </div>
      <div className="flex flex-col gap-2.5">
        <button
          type="button"
          role="checkbox"
          aria-checked={createOpt}
          onClick={() => setCreateOpt((v) => !v)}
          className="flex items-start gap-2.5 text-left"
        >
          <CheckBox checked={createOpt} />
          <span
            className={cn(
              "text-[14px] font-semibold text-[#083f3a] leading-[1.3] break-words [overflow-wrap:anywhere]",
              !createOpt && "opacity-45",
            )}
          >
            Create «{headline}»
          </span>
        </button>
        <button
          type="button"
          role="checkbox"
          aria-checked={makeActive && createOpt}
          disabled={!createOpt}
          onClick={() => setMakeActive((v) => !v)}
          className="flex items-start gap-2.5 text-left disabled:opacity-45"
        >
          <CheckBox checked={makeActive && createOpt} />
          <span className="text-[14px] font-semibold text-[#083f3a] leading-[1.3]">
            Make it the active option
          </span>
        </button>
      </div>
      <button
        onClick={() => setInstructing(true)}
        className="inline-flex items-center gap-1.5 mt-3 text-[12.5px] text-[#083f3a]/55 hover:text-[#083f3a] transition-colors"
      >
        <Ic path={PATHS.sparkles} size={12} /> Type a change for the AI…
      </button>
      <div className="mt-3.5 flex items-center gap-2 border-t border-[#eef1f0] pt-3">
        <button
          onClick={confirm}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-[9px] bg-[#006d67] text-white text-[13px] font-semibold hover:bg-[#005b56] transition-colors"
        >
          <Ic path={PATHS.check} size={14} /> Confirm
        </button>
        <button
          onClick={() => onResolve("rejected")}
          className="ml-auto text-[13px] text-[#083f3a]/50 hover:text-red-600 transition-colors px-1.5 py-2"
        >
          Dismiss
        </button>
      </div>
    </div>
  );
}

/**
 * Several SEPARATE things proposed in one turn (e.g. two options) → a stepper, one
 * per step (Back / Next). Each step has a checkbox to include/skip the change, plus
 * an extra checkbox for any secondary aspect (e.g. "make it the active option"); the
 * final "Save" commits everything still checked.
 */
function SteppedProposalCard({
  proposals,
  goal,
  onResolveOne,
  onApprove,
  onInstructOne,
  onExpand,
}: {
  proposals: Proposal[];
  goal?: Goal;
  onResolveOne: (id: string, status: "approved" | "rejected") => void;
  onApprove: (p: Proposal) => void;
  onInstructOne: (p: Proposal, instruction: string) => void;
  onExpand: (content: { title: string; body: string; html?: boolean }) => void;
}) {
  const [step, setStep] = useState(0);
  const [instructing, setInstructing] = useState(false);
  const [excluded, setExcluded] = useState<Set<string>>(new Set());
  const [noActive, setNoActive] = useState<Set<string>>(new Set());
  // Per-step unticked optional fields, keyed "proposalId::aspectId".
  const [aspectOff, setAspectOff] = useState<Set<string>>(new Set());
  const aspectKey = (id: string, aid: string) => `${id}::${aid}`;
  const toggleAspect = (key: string) =>
    setAspectOff((prev) => {
      const n = new Set(prev);
      if (n.has(key)) n.delete(key);
      else n.add(key);
      return n;
    });
  // Per-step unticked checklist items, keyed "proposalId#itemIndex".
  const [itemOff, setItemOff] = useState<Set<string>>(new Set());
  const itemKey = (id: string, i: number) => `${id}#${i}`;
  const toggleItem = (key: string) =>
    setItemOff((prev) => {
      const n = new Set(prev);
      if (n.has(key)) n.delete(key);
      else n.add(key);
      return n;
    });

  const settled = proposals.every((p) => p.status !== "pending");
  const total = proposals.length;
  const idx = Math.min(step, total - 1);
  const cur = proposals[idx];
  const { headline, detail, body } = proposalDisplay(cur, goal);
  const isCreate = CREATE_KINDS.has(cur.kind);
  const aspects = createAspects(cur);
  const summary = createSummary(cur);
  const curItems =
    (cur.kind === "target" || cur.kind === "task") &&
    cur.targetType === "checklist"
      ? cur.items
      : undefined;

  const isOff = (p: Proposal) => excluded.has(p.id) || p.status === "rejected";
  const includedCount = proposals.filter((p) => !isOff(p)).length;
  const toggleOff = (id: string) =>
    setExcluded((prev) => {
      const n = new Set(prev);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });
  const toggleNoActive = (id: string) =>
    setNoActive((prev) => {
      const n = new Set(prev);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });

  // Strip any unticked optional fields and checklist items, then re-apply the option
  // "make active" flag.
  const buildFinal = (p: Proposal): Proposal => {
    const ex = new Set(
      createAspects(p)
        .map((a) => a.id)
        .filter((aid) => aspectOff.has(aspectKey(p.id, aid))),
    );
    let out = applyExcludedAspects(p, ex);
    if (
      (p.kind === "target" || p.kind === "task") &&
      p.targetType === "checklist" &&
      p.items?.length
    ) {
      out = {
        ...out,
        items: p.items.filter((_, i) => !itemOff.has(itemKey(p.id, i))),
      };
    }
    return isOptionActivate(p) ? { ...out, done: !noActive.has(p.id) } : out;
  };
  const saveAll = () => {
    proposals.forEach((p) => {
      if (p.status !== "pending") return;
      if (isOff(p)) {
        onResolveOne(p.id, "rejected");
        return;
      }
      onResolveOne(p.id, "approved");
      onApprove(buildFinal(p));
    });
  };
  const dismissAll = () =>
    proposals.forEach((p) => {
      if (p.status === "pending") onResolveOne(p.id, "rejected");
    });

  if (settled) {
    const saved = proposals.filter((p) => p.status === "approved").length;
    return (
      <div className={CARD_CLS}>
        <div
          className={cn(
            "inline-flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full font-medium",
            saved > 0
              ? "bg-[#006d67]/10 text-[#006d67]"
              : "bg-black/6 text-[#083f3a]/50",
          )}
        >
          <Ic path={saved > 0 ? PATHS.check : PATHS.x} size={12} />
          {saved > 0
            ? `Saved ${saved} of ${total} change${total > 1 ? "s" : ""}`
            : "All dismissed"}
        </div>
      </div>
    );
  }

  const pct = Math.round(((idx + 1) / total) * 100);

  return (
    <div className={CARD_CLS}>
      <div className="flex items-center gap-2 mb-3">
        <span className="text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#006d67]">
          {total} change{total > 1 ? "s" : ""}
        </span>
        <span className="ml-auto text-[11px] font-medium text-[#083f3a]/45 tabular-nums">
          {idx + 1} / {total}
        </span>
        <button
          onClick={dismissAll}
          aria-label="Dismiss all"
          className="text-[#083f3a]/35 hover:text-red-600 transition-colors p-0.5 -mr-0.5"
        >
          <X size={16} />
        </button>
      </div>
      <div className="h-[5px] rounded-full bg-[#006d67]/12 overflow-hidden mb-3.5">
        <div
          className="h-full rounded-full bg-[#006d67] transition-all duration-300"
          style={{ width: `${pct}%` }}
        />
      </div>

      {instructing ? (
        <InstructBox
          headline={headline}
          onSend={(t) => {
            setInstructing(false);
            onInstructOne(cur, t);
          }}
          onCancel={() => setInstructing(false)}
        />
      ) : (
        <>
          <div className="flex flex-col gap-2.5 min-h-[64px]">
            <button
              type="button"
              role="checkbox"
              aria-checked={!isOff(cur)}
              onClick={() => toggleOff(cur.id)}
              disabled={cur.status === "rejected"}
              className="flex items-start gap-2.5 text-left"
            >
              <CheckBox checked={!isOff(cur)} />
              <div className={cn("flex-1 min-w-0", isOff(cur) && "opacity-45")}>
                <div className="text-[14px] font-semibold text-[#083f3a] leading-[1.3] break-words [overflow-wrap:anywhere]">
                  {headline}
                </div>
                {/* For creates, fields live in their own checkboxes below — never restate
                    them here. Non-create changes keep their summary line. */}
                {detail && !isCreate && (
                  <div className="text-[12.5px] text-[#083f3a]/55 mt-0.5 break-words [overflow-wrap:anywhere]">
                    {detail}
                  </div>
                )}
                {/* Checklist shows its items as real checkboxes below — skip the count line. */}
                {isCreate && summary && !curItems && (
                  <div className="text-[12.5px] text-[#083f3a]/55 mt-0.5 break-words [overflow-wrap:anywhere]">
                    {summary}
                  </div>
                )}
              </div>
            </button>
            {body && body.trim() && !isCreate && (
              <button
                onClick={() =>
                  onExpand({
                    title: headline,
                    body,
                    html: cur.kind === "note" || cur.kind === "edit_note",
                  })
                }
                className="self-start ml-[30px] inline-flex items-center gap-1.5 text-[12px] font-medium text-[#006d67] hover:text-[#005b56] transition-colors"
              >
                <Ic path={PATHS.expand} size={12} /> Read full content
              </button>
            )}
            {curItems?.length ? (
              <ChecklistItems
                items={curItems}
                disabled={isOff(cur)}
                excluded={
                  new Set(
                    curItems
                      .map((_, i) => i)
                      .filter((i) => itemOff.has(itemKey(cur.id, i))),
                  )
                }
                onToggle={(i) => toggleItem(itemKey(cur.id, i))}
              />
            ) : null}
            {aspects.map((a) => (
              <AspectRow
                key={a.id}
                label={a.label}
                body={a.body}
                headline={headline}
                checked={!aspectOff.has(aspectKey(cur.id, a.id)) && !isOff(cur)}
                disabled={isOff(cur)}
                onToggle={() => toggleAspect(aspectKey(cur.id, a.id))}
                onExpand={onExpand}
              />
            ))}
            {isOptionActivate(cur) && (
              <button
                type="button"
                role="checkbox"
                aria-checked={!noActive.has(cur.id) && !isOff(cur)}
                onClick={() => toggleNoActive(cur.id)}
                disabled={isOff(cur)}
                className="flex items-start gap-2.5 text-left disabled:opacity-45"
              >
                <CheckBox checked={!noActive.has(cur.id) && !isOff(cur)} />
                <span className="text-[13.5px] font-semibold text-[#083f3a]">
                  Make it the active option
                </span>
              </button>
            )}
          </div>

          <button
            onClick={() => setInstructing(true)}
            className="inline-flex items-center gap-1.5 mt-3 text-[12.5px] text-[#083f3a]/55 hover:text-[#083f3a] transition-colors"
          >
            <Ic path={PATHS.sparkles} size={12} /> Type a change for the AI…
          </button>

          <div className="mt-3.5 flex items-center justify-between border-t border-[#eef1f0] pt-3">
            <button
              onClick={() => setStep((s) => Math.max(0, s - 1))}
              disabled={idx === 0}
              className="inline-flex items-center gap-1 text-[13px] font-medium text-[#083f3a]/70 disabled:opacity-30 hover:text-[#083f3a] transition-colors"
            >
              <Ic path={PATHS.chevron} size={13} className="rotate-90" /> Back
            </button>
            {idx < total - 1 ? (
              <button
                onClick={() => setStep((s) => Math.min(total - 1, s + 1))}
                className="inline-flex items-center gap-1 text-[13px] font-semibold text-[#006d67] hover:text-[#005b56] transition-colors"
              >
                Next{" "}
                <Ic path={PATHS.chevron} size={13} className="-rotate-90" />
              </button>
            ) : (
              <span className="text-[12px] text-[#083f3a]/40">
                End of review
              </span>
            )}
          </div>

          <div className="mt-3 flex flex-col gap-1.5">
            <button
              onClick={saveAll}
              disabled={includedCount === 0}
              className="w-full inline-flex items-center justify-center gap-1.5 px-3.5 py-2.5 rounded-[10px] bg-[#006d67] text-white text-[13.5px] font-semibold hover:bg-[#005b56] disabled:opacity-40 transition-colors"
            >
              <Ic path={PATHS.check} size={15} />
              {includedCount === total
                ? `Save all ${total}`
                : `Save ${includedCount} of ${total}`}
            </button>
            <button
              onClick={dismissAll}
              className="text-[12.5px] text-[#083f3a]/45 hover:text-red-600 transition-colors py-1"
            >
              Dismiss all
            </button>
          </div>
        </>
      )}
    </div>
  );
}

const CREATE_KINDS = new Set<ProposalKind>(["new_goal", "target", "task"]);
// Resource creations go through the normal ProposalCard (Accept), but also get an
// "Open" shortcut to the goal's Resources section once added.
const RESOURCE_CREATE_KINDS = new Set<ProposalKind>(["note", "link", "email"]);

/** A single aspect (optional field) checkbox row used by both the single-create card and
 *  the stepper. Description-type aspects get a "Read full content" expander. */
function AspectRow({
  label,
  body,
  checked,
  disabled,
  onToggle,
  onExpand,
  headline,
}: {
  label: string;
  body?: string;
  checked: boolean;
  disabled?: boolean;
  onToggle: () => void;
  onExpand?: (content: { title: string; body: string; html?: boolean }) => void;
  headline: string;
}) {
  return (
    <div className="flex flex-col gap-1">
      <button
        type="button"
        role="checkbox"
        aria-checked={checked}
        disabled={disabled}
        onClick={onToggle}
        className="flex items-start gap-2.5 text-left disabled:opacity-45"
      >
        <CheckBox checked={checked} />
        <span className="text-[13.5px] font-medium text-[#083f3a]/85 leading-[1.3] break-words [overflow-wrap:anywhere]">
          {label}
        </span>
      </button>
      {body && body.trim() && onExpand && (
        <button
          onClick={() => onExpand({ title: headline, body })}
          className="self-start ml-[30px] inline-flex items-center gap-1.5 text-[12px] font-medium text-[#006d67] hover:text-[#005b56] transition-colors"
        >
          <Ic path={PATHS.expand} size={12} /> Read full content
        </button>
      )}
    </div>
  );
}

/** Real, interactive checkboxes for a checklist target's items (no markdown preview, no
 *  bullets). A ticked item is created; unticking one drops it from the new target. */
function ChecklistItems({
  items,
  excluded,
  disabled,
  onToggle,
}: {
  items: { text: string; done?: boolean; deadline?: string }[];
  excluded: Set<number>;
  disabled?: boolean;
  onToggle: (i: number) => void;
}) {
  return (
    <div className="flex flex-col gap-2 ml-[30px]">
      {items.map((it, i) => {
        const on = !excluded.has(i) && !disabled;
        return (
          <button
            key={i}
            type="button"
            role="checkbox"
            aria-checked={on}
            disabled={disabled}
            onClick={() => onToggle(i)}
            className="flex items-start gap-2.5 text-left disabled:opacity-45"
          >
            <CheckBox checked={on} />
            <span className="text-[13px] text-[#083f3a]/85 leading-[1.3] break-words [overflow-wrap:anywhere]">
              {it.text}
              {it.deadline ? ` · due ${fmtDeadline(it.deadline)}` : ""}
            </span>
          </button>
        );
      })}
    </div>
  );
}

/** The settled "Goal created / Target added / Dismissed" pill + Open shortcut, shared by
 *  the one-tap and field-checklist create cards. */
function CreateSettled({
  p,
  isGoal,
  onOpen,
}: {
  p: Proposal;
  isGoal: boolean;
  onOpen: (ref: {
    kind: "goal" | "target" | "resource";
    goalId: string;
  }) => void;
}) {
  return (
    <div className="mt-3 flex items-center gap-2 flex-wrap">
      <div
        className={cn(
          "inline-flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full font-medium",
          p.status === "approved"
            ? "bg-[#006d67]/10 text-[#006d67]"
            : "bg-black/6 text-[#083f3a]/50",
        )}
      >
        <Ic path={p.status === "approved" ? PATHS.check : PATHS.x} size={12} />
        {p.status === "approved"
          ? isGoal
            ? "Goal created"
            : "Target added"
          : "Dismissed"}
      </div>
      {p.status === "approved" && p.createdRef && (
        <button
          onClick={() => onOpen(p.createdRef!)}
          className="ml-auto inline-flex items-center gap-1.5 px-3 py-1.5 rounded-[9px] bg-[#006d67] text-white text-[12.5px] font-semibold hover:bg-[#005b56] transition-colors"
        >
          <Ic path={PATHS.switch_} size={13} />{" "}
          {p.createdRef.kind === "goal" ? "Open goal" : "Open target"}
        </button>
      )}
    </div>
  );
}

/**
 * Creating one entity that carries several optional fields → a checklist: the first
 * checkbox is the entity itself ("Create «Goal 1»"), then one checkbox per field
 * (confidence, deadline, description). Unticking a field drops it from the save; unticking
 * the entity dismisses the whole creation. Mirrors the option card's two-checkbox pattern.
 */
function CreateChecklistCard({
  p,
  goal,
  aspects,
  onOpen,
  onResolve,
  onCreate,
  onInstruct,
  onExpand,
}: {
  p: Proposal;
  goal?: Goal;
  aspects: { id: string; label: string; body?: string }[];
  onOpen: (ref: {
    kind: "goal" | "target" | "resource";
    goalId: string;
  }) => void;
  onResolve: (status: "approved" | "rejected") => void;
  onCreate: (p: Proposal) => void;
  onInstruct: (instruction: string) => void;
  onExpand: (content: { title: string; body: string; html?: boolean }) => void;
}) {
  const isGoal = p.kind === "new_goal";
  const meta = KIND_META[p.kind] || KIND_META.target;
  const settled = p.status !== "pending";
  const { headline } = proposalDisplay(p, goal);
  const [createOn, setCreateOn] = useState(true);
  const [excluded, setExcluded] = useState<Set<string>>(new Set());
  const [exItems, setExItems] = useState<Set<number>>(new Set());
  const [instructing, setInstructing] = useState(false);
  const items =
    (p.kind === "target" || p.kind === "task") && p.targetType === "checklist"
      ? p.items
      : undefined;

  if (settled) {
    return (
      <div className={CARD_CLS}>
        <div className="mb-2">
          <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#006d67]">
            <Ic path={meta.icon} size={12} /> {meta.label}
          </span>
        </div>
        <div className="font-['Playfair_Display'] text-[17px] font-semibold leading-[1.25] break-words [overflow-wrap:anywhere]">
          {headline}
        </div>
        <CreateSettled p={p} isGoal={isGoal} onOpen={onOpen} />
      </div>
    );
  }

  if (instructing) {
    return (
      <div className={CARD_CLS}>
        <InstructBox
          headline={headline}
          onSend={(t) => {
            setInstructing(false);
            onInstruct(t);
          }}
          onCancel={() => setInstructing(false)}
        />
      </div>
    );
  }

  const toggle = (id: string) =>
    setExcluded((prev) => {
      const n = new Set(prev);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });
  const toggleItem = (i: number) =>
    setExItems((prev) => {
      const n = new Set(prev);
      if (n.has(i)) n.delete(i);
      else n.add(i);
      return n;
    });
  const confirm = () => {
    if (!createOn) {
      onResolve("rejected");
      return;
    }
    onResolve("approved");
    let out = applyExcludedAspects(p, excluded);
    // Drop any checklist item the user unticked — it isn't created.
    if (items?.length)
      out = { ...out, items: items.filter((_, i) => !exItems.has(i)) };
    onCreate(out);
  };

  return (
    <div className={CARD_CLS}>
      <div className="mb-3">
        <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#006d67]">
          <Ic path={meta.icon} size={12} /> {meta.label}
        </span>
      </div>
      <div className="flex flex-col gap-2.5">
        <button
          type="button"
          role="checkbox"
          aria-checked={createOn}
          onClick={() => setCreateOn((v) => !v)}
          className="flex items-start gap-2.5 text-left"
        >
          <CheckBox checked={createOn} />
          <span
            className={cn(
              "text-[14px] font-semibold text-[#083f3a] leading-[1.3] break-words [overflow-wrap:anywhere]",
              !createOn && "opacity-45",
            )}
          >
            {isGoal ? "Create" : "Add"} «{headline}»
          </span>
        </button>
        {/* Numeric measure stays a one-line summary; a checklist shows its items as real,
            tickable checkboxes instead (so the count line would be redundant). */}
        {createSummary(p) && !items && (
          <div className="ml-[30px] -mt-1 text-[12.5px] text-[#083f3a]/55">
            {createSummary(p)}
          </div>
        )}
        {items?.length ? (
          <ChecklistItems
            items={items}
            excluded={exItems}
            disabled={!createOn}
            onToggle={toggleItem}
          />
        ) : null}
        {aspects.map((a) => (
          <AspectRow
            key={a.id}
            label={a.label}
            body={a.body}
            headline={headline}
            checked={!excluded.has(a.id) && createOn}
            disabled={!createOn}
            onToggle={() => toggle(a.id)}
            onExpand={onExpand}
          />
        ))}
      </div>
      <button
        onClick={() => setInstructing(true)}
        className="inline-flex items-center gap-1.5 mt-3 text-[12.5px] text-[#083f3a]/55 hover:text-[#083f3a] transition-colors"
      >
        <Ic path={PATHS.sparkles} size={12} /> Type a change for the AI…
      </button>
      <div className="mt-3.5 flex items-center gap-2 border-t border-[#eef1f0] pt-3">
        <button
          onClick={confirm}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-[9px] bg-[#006d67] text-white text-[13px] font-semibold hover:bg-[#005b56] transition-colors"
        >
          <Ic path={PATHS.check} size={14} />{" "}
          {isGoal ? "Create goal" : "Add target"}
        </button>
        <button
          onClick={() => onResolve("rejected")}
          className="ml-auto text-[13px] text-[#083f3a]/50 hover:text-red-600 transition-colors px-1.5 py-2"
        >
          Dismiss
        </button>
      </div>
    </div>
  );
}

/**
 * One-tap confirmation for creating a goal/target. Deliberately minimal — just the
 * name + Create/Dismiss — NOT a multi-step wizard. Once created, an "Open goal/target"
 * shortcut appears so the user can jump straight to it.
 */
function CreateConfirmCard({
  p,
  goal,
  onOpen,
  onResolve,
  onCreate,
}: {
  p: Proposal;
  goal?: Goal;
  onOpen: (ref: {
    kind: "goal" | "target" | "resource";
    goalId: string;
  }) => void;
  onResolve: (status: "approved" | "rejected") => void;
  onCreate: (p: Proposal) => void;
}) {
  const isGoal = p.kind === "new_goal";
  const meta = KIND_META[p.kind] || KIND_META.target;
  const settled = p.status !== "pending";
  const { headline } = proposalDisplay(p, goal);
  // The kind badge already says "New goal" / "New target", so don't repeat it under the
  // title. For a target show its TYPE instead; a bare goal needs no second line.
  const typeLine = isGoal ? undefined : "Done / not done";

  return (
    <div className={CARD_CLS}>
      <div className="mb-2">
        <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#006d67]">
          <Ic path={meta.icon} size={12} /> {meta.label}
        </span>
      </div>
      <div className="font-['Playfair_Display'] text-[17px] font-semibold leading-[1.25] break-words [overflow-wrap:anywhere]">
        {headline}
      </div>
      {typeLine && (
        <p className="mt-1.5 text-[13.5px] leading-[1.5] text-[#083f3a]/60">
          {typeLine}
        </p>
      )}

      {settled ? (
        <CreateSettled p={p} isGoal={isGoal} onOpen={onOpen} />
      ) : (
        <div className="mt-3.5 flex items-center gap-2">
          <button
            onClick={() => {
              onResolve("approved");
              onCreate(p);
            }}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-[9px] bg-[#006d67] text-white text-[13px] font-semibold hover:bg-[#005b56] transition-colors"
          >
            <Ic path={PATHS.check} size={14} />{" "}
            {isGoal ? "Create goal" : "Add target"}
          </button>
          <button
            onClick={() => onResolve("rejected")}
            className="ml-auto text-[13px] text-[#083f3a]/50 hover:text-red-600 transition-colors px-1.5 py-2"
          >
            Dismiss
          </button>
        </div>
      )}
    </div>
  );
}

/**
 * Once a proposal card is resolved, the full card is gone — only this compact result
 * stays in the chat (a check + what was saved, with an "Open" link to the new item),
 * like a collapsed action summary. Dismissed changes show a muted "Dismissed".
 */
function ResultSummary({
  proposals,
  goal,
  onOpen,
}: {
  proposals: Proposal[];
  goal?: Goal;
  onOpen: (ref: {
    kind: "goal" | "target" | "resource";
    goalId: string;
  }) => void;
}) {
  const approved = proposals.filter((p) => p.status === "approved");
  if (approved.length === 0) {
    return (
      <div className="inline-flex items-center gap-1.5 self-start text-[12.5px] text-white/55 bg-white/8 px-3 py-1.5 rounded-full">
        <Ic path={PATHS.x} size={12} /> Dismissed
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-1.5 self-start max-w-full">
      {approved.map((p) => {
        const { headline } = proposalDisplay(p, goal);
        return (
          <div
            key={p.id}
            className="inline-flex items-center gap-2 self-start text-[12.5px] text-white/80 bg-white/10 px-3 py-1.5 rounded-full max-w-full"
          >
            <Ic path={PATHS.check} size={12} className="shrink-0" />
            <span className="truncate">{headline}</span>
            {p.createdRef && (
              <button
                onClick={() => onOpen(p.createdRef!)}
                className="shrink-0 inline-flex items-center gap-1 font-semibold text-white hover:underline"
              >
                <Ic path={PATHS.switch_} size={12} /> Open
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}

/** Renders the proposals attached to one assistant message: a single polished
 *  card when there's one change, or one stepped card when there are several. */
function ProposalGroup({
  proposals,
  goal,
  onResolveOne,
  onApprove,
  onCreateProposal,
  onOpenCreated,
  onInstructOne,
  onExpand,
}: {
  proposals: Proposal[];
  goal?: Goal;
  onResolveOne: (id: string, status: "approved" | "rejected") => void;
  onApprove: (p: Proposal) => void;
  onCreateProposal: (p: Proposal) => void;
  onOpenCreated: (ref: {
    kind: "goal" | "target" | "resource";
    goalId: string;
  }) => void;
  onInstructOne: (p: Proposal, instruction: string) => void;
  onExpand: (content: { title: string; body: string; html?: boolean }) => void;
}) {
  if (proposals.length === 0) return null;
  if (proposals.length === 1) {
    const p = proposals[0];
    // One option that's also being made active → two checkboxes (create + activate).
    if (isOptionActivate(p)) {
      return (
        <OptionAspectCard
          p={p}
          goal={goal}
          onResolve={(status) => onResolveOne(p.id, status)}
          onApprove={onApprove}
          onInstruct={(t) => onInstructOne(p, t)}
        />
      );
    }
    // Creating: if the entity carries optional fields (deadline, confidence, …) OR is a
    // structured target (numeric/checklist, which need their measure + preview), show the
    // checklist card; a bare name → a one-tap confirm card.
    if (CREATE_KINDS.has(p.kind)) {
      const aspects = createAspects(p);
      const isStructured =
        (p.kind === "target" || p.kind === "task") &&
        (p.targetType === "numeric" || p.targetType === "checklist");
      if (aspects.length > 0 || isStructured) {
        return (
          <CreateChecklistCard
            p={p}
            goal={goal}
            aspects={aspects}
            onOpen={onOpenCreated}
            onResolve={(status) => onResolveOne(p.id, status)}
            onCreate={onCreateProposal}
            onInstruct={(t) => onInstructOne(p, t)}
            onExpand={onExpand}
          />
        );
      }
      return (
        <CreateConfirmCard
          p={p}
          goal={goal}
          onOpen={onOpenCreated}
          onResolve={(status) => onResolveOne(p.id, status)}
          onCreate={onCreateProposal}
        />
      );
    }
    return (
      <ProposalCard
        p={p}
        goal={goal}
        onExpand={onExpand}
        onOpen={onOpenCreated}
        onResolve={(status) => onResolveOne(p.id, status)}
        onApprove={onApprove}
        onInstruct={(t) => onInstructOne(p, t)}
      />
    );
  }
  // Several separate things → stepper, one per step (each step may have checkboxes).
  return (
    <SteppedProposalCard
      proposals={proposals}
      goal={goal}
      onResolveOne={onResolveOne}
      onApprove={onApprove}
      onInstructOne={onInstructOne}
      onExpand={onExpand}
    />
  );
}

// ── GROW start overlay ─────────────────────────────────────────────────────

function GrowStartOverlay({
  onStart,
  onCancel,
}: {
  onStart: (mins: number, focus: string) => void;
  onCancel: () => void;
}) {
  const [mins, setMins] = useState(30);
  const [focus, setFocus] = useState("");

  return (
    <div className="absolute inset-0 z-40 flex items-end bg-[rgba(8,40,38,0.4)] backdrop-blur-[2px]">
      <div
        className="w-full bg-white text-[#083f3a] rounded-t-[22px] px-5 pt-6 pb-5"
        style={{ animation: "slideUp 0.3s cubic-bezier(0.2,0.8,0.2,1) both" }}
      >
        <span className="inline-flex items-center gap-1.5 text-[11px] uppercase tracking-[0.07em] font-bold text-[#006d67]">
          <Ic path={PATHS.leaf} size={14} className="text-[#006d67]" /> GROW
          session
        </span>
        <h3 className="font-['Playfair_Display'] text-[22px] font-semibold mt-2.5 mb-1 leading-[1.18]">
          Focused time on a single goal
        </h3>
        <p className="text-[13.5px] text-[#083f3a]/60 mb-4 leading-[1.5]">
          A conversation without rush. I'll help you get clarity — the decisions
          stay yours.
        </p>
        <div className="grid grid-cols-4 gap-2 mb-3.5">
          {([15, 30, 45, 60] as const).map((m) => (
            <button
              key={m}
              onClick={() => setMins(m)}
              className={cn(
                "flex flex-col items-center py-2.5 rounded-xl border text-[#083f3a] transition-colors",
                mins === m
                  ? "border-[#006d67] bg-[#e7f3f1] text-[#006d67]"
                  : "border-[#e6e4df] hover:border-[#006d67]/40",
              )}
            >
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
        <button
          onClick={() => onStart(mins, focus)}
          className="w-full flex items-center justify-center gap-2 py-3.5 rounded-xl bg-[#006d67] text-white text-[14.5px] font-semibold hover:bg-[#005b56] transition-colors"
        >
          Start session · {mins} min
        </button>
        <button
          onClick={onCancel}
          className="block mx-auto mt-2 text-[13px] text-[#083f3a]/50 hover:text-[#083f3a] transition-colors py-1.5"
        >
          Cancel
        </button>
      </div>
      <style>{`@keyframes slideUp { from { transform: translateY(34px); opacity: 0.25; } to { transform: translateY(0); opacity: 1; } }`}</style>
    </div>
  );
}

// ── AI tool DATA proposal card (approve an edit/delete of a tool row) ───────

function ToolDataProposalCard({
  proposal,
  onResolve,
}: {
  proposal: ToolDataProposal;
  onResolve: (status: "applied" | "dismissed") => void;
}) {
  const [saving, setSaving] = useState(false);

  if (proposal.status === "dismissed") return null;
  if (proposal.status === "applied") {
    return (
      <div className="mt-2 inline-flex items-center gap-2 self-start rounded-full bg-white/10 px-3 py-1.5 text-[12.5px] text-white/80">
        <Ic path={PATHS.check} size={12} />{" "}
        {proposal.op === "delete" ? "Row deleted." : "Row updated."}
      </div>
    );
  }

  const accept = async () => {
    setSaving(true);
    try {
      if (proposal.op === "delete") {
        await deleteRecord(proposal.toolId, proposal.recordId);
        toast.success("Row deleted");
      } else {
        await updateRecord(proposal.toolId, proposal.recordId, proposal.data);
        toast.success("Row updated");
      }
      // Refresh an open window for this tool so the change is visible at once.
      useTools.getState().bumpRecords(proposal.toolId);
      onResolve("applied");
    } catch (e) {
      toast.error(
        e instanceof Error ? e.message : "Couldn't apply the change.",
      );
      setSaving(false);
    }
  };

  const isDelete = proposal.op === "delete";
  return (
    <div className="mt-2 rounded-[14px] border border-white/20 bg-white p-3.5 text-[#083f3a] shadow-[0_6px_20px_-14px_rgba(0,0,0,0.4)]">
      <span
        className={cn(
          "inline-flex items-center gap-1.5 text-[10.5px] font-semibold uppercase tracking-[0.07em]",
          isDelete ? "text-red-600" : "text-[#006d67]",
        )}
      >
        <Ic path={isDelete ? PATHS.trash : PATHS.pencil} size={12} />{" "}
        {isDelete ? "Delete row" : "Edit row"}
      </span>
      <p className="mt-1.5 text-[13px] leading-[1.5] text-[#083f3a]/70">
        {isDelete
          ? "The AI suggests removing a row from this tool."
          : "The AI suggests changing a row:"}
        {!isDelete && proposal.data != null && (
          <span className="mt-1 block rounded-[8px] bg-[#fbf9f4] px-2 py-1.5 font-mono text-[11.5px] text-[#083f3a]/80">
            {Object.entries(proposal.data as Record<string, unknown>)
              .map(([k, v]) => `${k}: ${String(v)}`)
              .join("  ·  ")}
          </span>
        )}
      </p>
      <div className="mt-3 flex items-center gap-2">
        <button
          onClick={accept}
          disabled={saving}
          className={cn(
            "inline-flex items-center gap-1.5 rounded-[9px] px-3.5 py-2 text-[13px] font-semibold text-white disabled:opacity-50",
            isDelete
              ? "bg-red-600 hover:bg-red-700"
              : "bg-[#006d67] hover:bg-[#005b56]",
          )}
        >
          <Ic path={PATHS.check} size={14} /> {isDelete ? "Delete" : "Apply"}
        </button>
        <button
          onClick={() => onResolve("dismissed")}
          className="ml-auto px-1.5 text-[13px] text-[#083f3a]/50 hover:text-[#083f3a]"
        >
          Dismiss
        </button>
      </div>
    </div>
  );
}

// ── AI tool proposal card (preview + approve a Personal Tool) ───────────────

/**
 * Read-only preview of the rows the user supplied up-front, so they can verify
 * their REAL data was captured (not fabricated) before approving the tool.
 */
function ToolRecordsPreview({
  schemaJson,
  records,
}: {
  schemaJson: string;
  records: Record<string, unknown>[];
}) {
  const schema = parseSchema(schemaJson);
  if (!schema) return null;
  return (
    <div className="mt-2 rounded-[10px] border border-[#e6e4df] bg-white p-2.5">
      <p className="mb-1.5 text-[10.5px] font-semibold uppercase tracking-[0.07em] text-[#006d67]">
        {records.length} {records.length === 1 ? "entry" : "entries"} to add
      </p>
      <ul className="space-y-1">
        {records.map((row, i) => (
          <li key={i} className="text-[12px] leading-[1.5] text-[#083f3a]/80">
            {schema.columns
              .map((c) => {
                const v = formatCell(c.primitive, row[c.key]);
                return v === "—" ? null : `${columnLabel(c)}: ${v}`;
              })
              .filter(Boolean)
              .join(" · ") || "—"}
          </li>
        ))}
      </ul>
    </div>
  );
}

function ToolProposalCard({
  proposal,
  onResolve,
}: {
  proposal: ToolProposal;
  onResolve: (status: "created" | "dismissed") => void;
}) {
  const [saving, setSaving] = useState(false);
  const isUpdate = proposal.op === "update";
  const tools = useTools((s) => s.tools);
  const existing = isUpdate
    ? tools.find((t) => t.id === proposal.toolId)
    : undefined;
  const displayName = proposal.name || existing?.name || "Tool";
  // A minimal Tool shape so the existing read-only renderer can preview it.
  const previewTool = {
    id: proposal.toolId ?? -1,
    goalId: proposal.goalId ?? existing?.goalId ?? null,
    name: displayName,
    schemaJson: JSON.stringify(proposal.schema),
    placement: proposal.placement as Tool["placement"],
    createdBy: "ai" as const,
    createdAt: new Date().toISOString(),
  };

  if (proposal.status === "dismissed") return null;
  if (proposal.status === "created") {
    return (
      <div className="mt-2 inline-flex items-center gap-2 self-start rounded-full bg-white/10 px-3 py-1.5 text-[12.5px] text-white/80">
        <Ic path={PATHS.check} size={12} /> {isUpdate ? "Updated" : "Added"} “
        {displayName}” — open it from the Tools button.
      </div>
    );
  }

  const accept = async () => {
    setSaving(true);
    try {
      if (isUpdate && proposal.toolId != null) {
        const updated = await updateTool(proposal.toolId, {
          name: proposal.name || undefined,
          schemaJson: JSON.stringify(proposal.schema),
        });
        // Replace it in the shared store + refresh the open window's renderer,
        // and make sure the tool is visible so the change is obvious.
        useTools.getState().applyToolUpdate(updated);
        useToolWindows.getState().open(updated.id);
        toast.success(`“${updated.name}” updated`);
        onResolve("created");
        return;
      }
      const created = await createTool({
        name: proposal.name,
        schemaJson: JSON.stringify(proposal.schema),
        placement: proposal.placement,
        goalId: proposal.goalId ?? null,
        createdBy: "ai",
      });
      // If the user supplied data up-front, the tool is created already filled:
      // write each (already server-validated) row in order. One failure doesn't
      // abort the rest — the tool still exists with whatever rows succeeded.
      let added = 0;
      for (const row of proposal.records ?? []) {
        try {
          await addRecord(created.id, row);
          added++;
        } catch {
          /* skip a row that won't save; the user can add it by hand */
        }
      }
      // Publish to the shared store so it shows up instantly everywhere, and
      // open it as a floating window so the user can use it right away.
      useTools.getState().addTool(created);
      useToolWindows.getState().open(created.id);
      toast.success(
        added > 0
          ? `“${proposal.name}” added with ${added} ${added === 1 ? "entry" : "entries"}`
          : `“${proposal.name}” added to your tools`,
      );
      onResolve("created");
    } catch (e) {
      toast.error(
        e instanceof Error
          ? e.message
          : isUpdate
            ? "Couldn't update the tool."
            : "Couldn't create the tool.",
      );
      setSaving(false);
    }
  };

  return (
    <div className="mt-2 rounded-[14px] border border-white/20 bg-white p-3.5 text-[#083f3a] shadow-[0_6px_20px_-14px_rgba(0,0,0,0.4)]">
      <span className="inline-flex items-center gap-1.5 text-[10.5px] font-semibold uppercase tracking-[0.07em] text-[#006d67]">
        <Ic path={PATHS.sparkles} size={12} className="text-[#006d67]" />{" "}
        {isUpdate ? "Tool update" : "New tool"}
      </span>
      <h4 className="mt-1.5 font-semibold">{displayName}</h4>
      {proposal.reasoning && (
        <p className="mt-0.5 text-[12.5px] leading-[1.5] text-[#083f3a]/60">
          {proposal.reasoning}
        </p>
      )}
      <div className="mt-2.5 rounded-[10px] border border-[#e6e4df] bg-[#fbf9f4] p-2.5">
        <ToolRenderer tool={previewTool} preview />
      </div>
      {proposal.records && proposal.records.length > 0 && (
        <ToolRecordsPreview
          schemaJson={previewTool.schemaJson}
          records={proposal.records}
        />
      )}
      <div className="mt-3 flex items-center gap-2">
        <button
          onClick={accept}
          disabled={saving}
          className="inline-flex items-center gap-1.5 rounded-[9px] bg-[#006d67] px-3.5 py-2 text-[13px] font-semibold text-white hover:bg-[#005b56] disabled:opacity-50"
        >
          <Ic path={PATHS.check} size={14} />{" "}
          {isUpdate ? "Apply changes" : "Add tool"}
        </button>
        <button
          onClick={() => onResolve("dismissed")}
          className="ml-auto px-1.5 text-[13px] text-[#083f3a]/50 hover:text-[#083f3a]"
        >
          Dismiss
        </button>
      </div>
    </div>
  );
}

// ── GROW end card ──────────────────────────────────────────────────────────

function GrowEndCard({
  proposals,
  memory,
  revising,
  onRevise,
  onSave,
  onDiscard,
}: {
  proposals: number;
  memory: string | null;
  revising: boolean;
  onRevise: (instruction: string) => void;
  onSave: () => void;
  onDiscard: () => void;
}) {
  const [reviseDraft, setReviseDraft] = useState("");
  const sendRevise = () => {
    const t = reviseDraft.trim();
    if (!t || revising) return;
    onRevise(t);
    setReviseDraft("");
  };

  return (
    <div className="rounded-[14px] border border-white/20 bg-white text-[#083f3a] p-4 shadow-[0_6px_20px_-14px_rgba(0,0,0,0.4)]">
      <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#006d67]">
        <Ic path={PATHS.brain} size={12} className="text-[#006d67]" /> Session
        wrap-up
      </span>
      <p className="mt-2 text-[13.5px] leading-[1.5] text-[#083f3a]/60">
        {memory
          ? "This is what will be saved as the session memory — next time we'll continue from it."
          : "Save what I learned about this goal? Next time we'll continue instead of starting from scratch."}
      </p>
      {memory && (
        <div
          className={cn(
            "mt-2.5 rounded-[9px] border border-[#e6e4df] bg-[#fbf9f4] px-3 py-2.5 max-h-44 overflow-y-auto text-[12.5px] leading-[1.55] text-[#083f3a]/85 whitespace-pre-wrap select-text",
            revising && "opacity-50",
          )}
        >
          {memory}
        </div>
      )}
      {memory && (
        <div className="mt-2 flex items-center gap-2 rounded-[9px] border border-[#e6e4df] bg-white px-3 py-1.5 focus-within:border-[#006d67] transition-colors">
          {revising ? (
            <span className="h-3.5 w-3.5 shrink-0 rounded-full border-2 border-[#006d67]/30 border-t-[#006d67] animate-spin" />
          ) : (
            <Ic
              path={PATHS.pencil}
              size={13}
              className="shrink-0 text-[#083f3a]/40"
            />
          )}
          <input
            value={reviseDraft}
            onChange={(e) => setReviseDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") sendRevise();
            }}
            disabled={revising}
            placeholder={
              revising ? "Revising…" : "Want changes? Tell the AI what to fix…"
            }
            className="flex-1 bg-transparent outline-none text-[13px] text-[#083f3a] placeholder:text-[#083f3a]/35 min-h-[30px] disabled:opacity-60"
          />
          {reviseDraft.trim() && !revising && (
            <button
              onClick={sendRevise}
              className="shrink-0 text-[12.5px] font-semibold text-[#006d67] hover:text-[#005b56] px-1"
            >
              Revise
            </button>
          )}
        </div>
      )}
      {proposals > 0 && (
        <div className="mt-2.5 flex items-center gap-2 px-3 py-2 rounded-[9px] bg-[#e7f3f1] text-[#006d67] text-[12.5px]">
          <Ic path={PATHS.target} size={12} /> {proposals} proposal
          {proposals === 1 ? "" : "s"} still awaiting your decision
        </div>
      )}
      <div className="mt-3.5 flex items-center gap-2">
        <button
          onClick={onSave}
          disabled={revising}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#006d67] text-white text-[13px] font-semibold hover:bg-[#005b56] transition-colors disabled:opacity-50"
        >
          <Ic path={PATHS.check} size={14} /> Save memory
        </button>
        <button
          onClick={onDiscard}
          className="ml-auto text-[13px] text-[#083f3a]/50 hover:text-[#083f3a] transition-colors px-1.5"
        >
          Don't save
        </button>
      </div>
    </div>
  );
}

// ── Provider sheet ─────────────────────────────────────────────────────────

function ProviderSheet({
  providers,
  activeId,
  onActivate,
  onSaveKey,
  onModelChange,
  tavily,
  onSaveTavily,
  onClose,
}: {
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
    <div
      className="absolute inset-0 z-45 flex items-end bg-[rgba(8,40,38,0.42)] backdrop-blur-[2px]"
      onClick={onClose}
    >
      <div
        className="w-full max-h-[88%] overflow-y-auto bg-white text-[#083f3a] rounded-t-[22px] px-5 pt-3 pb-5"
        onClick={(e) => e.stopPropagation()}
        style={{ animation: "slideUp 0.3s cubic-bezier(0.2,0.8,0.2,1) both" }}
      >
        <div className="w-[38px] h-1 rounded-full bg-[#e6e4df] mx-auto mb-3.5" />
        <div className="flex items-start justify-between gap-2 mb-1">
          <div>
            <span className="inline-flex items-center gap-1.5 text-[11px] uppercase tracking-[0.07em] font-bold text-[#006d67]">
              <Ic path={PATHS.key} size={14} className="text-[#006d67]" /> Bring
              your own key
            </span>
            <h3 className="font-['Playfair_Display'] text-[22px] font-semibold mt-1.5 leading-[1.18]">
              AI providers
            </h3>
          </div>
          <button
            onClick={onClose}
            className="w-[34px] h-[34px] grid place-items-center rounded-[9px] text-[#083f3a]/50 hover:bg-black/5 hover:text-[#083f3a] transition-colors"
          >
            <Ic path={PATHS.x} size={16} />
          </button>
        </div>
        <p className="text-[13.5px] text-[#083f3a]/60 mb-4 leading-[1.5]">
          Keys are stored encrypted on your account. Keep several connected and
          switch anytime.
        </p>

        <div className="flex flex-col gap-3">
          {providers.map((p) => {
            const isActive = p.id === activeId && p.connected;
            const dropOpen = openDropdown === p.id;
            const fetchedModels = modelLists[p.id];
            const isLoadingMdl = loadingModels === p.id;

            return (
              <div
                key={p.id}
                className={cn(
                  "border rounded-xl p-3.5",
                  isActive
                    ? "border-[#006d67] bg-[#e7f3f1]/50"
                    : "border-[#e6e4df]",
                )}
              >
                {/* Header row */}
                <div className="flex items-center justify-between gap-2">
                  <div>
                    <span className="font-semibold text-[15px]">
                      {p.vendor}
                    </span>
                    <span className="ml-2 text-[12px] text-[#083f3a]/50">
                      {p.context}
                    </span>
                  </div>
                  {isActive ? (
                    <span className="inline-flex items-center gap-1 text-[12px] font-semibold text-[#006d67] bg-[#006d67]/10 px-2 py-0.5 rounded-full">
                      <Ic path={PATHS.check} size={11} /> Active
                    </span>
                  ) : p.connected ? (
                    <button
                      onClick={() => onActivate(p.id)}
                      className="inline-flex items-center gap-1 text-[12px] font-medium text-[#083f3a] border border-[#e6e4df] px-2.5 py-1 rounded-lg hover:border-[#006d67]/40 transition-colors"
                    >
                      <Ic path={PATHS.switch_} size={12} /> Use this
                    </button>
                  ) : (
                    <span className="text-[12px] text-[#083f3a]/40">
                      Not connected
                    </span>
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
                      <Ic
                        path={PATHS.chevron}
                        size={14}
                        className={cn(
                          "shrink-0 text-[#083f3a]/50 transition-transform duration-150",
                          dropOpen && "rotate-180",
                        )}
                      />
                    </button>

                    {dropOpen && (
                      <div className="absolute top-full left-0 right-0 z-50 mt-1 bg-white border border-[#e6e4df] rounded-xl shadow-lg overflow-hidden">
                        {isLoadingMdl ? (
                          <div className="px-3 py-3 text-[13px] text-[#083f3a]/50 text-center">
                            Loading models…
                          </div>
                        ) : (fetchedModels ?? p.models).length === 0 ? (
                          <div className="px-3 py-3 text-[13px] text-[#083f3a]/50 text-center">
                            No models found
                          </div>
                        ) : (
                          <div className="max-h-[200px] overflow-y-auto">
                            {(fetchedModels ?? p.models).map((m) => (
                              <button
                                key={m}
                                onClick={() => {
                                  onModelChange(p.id, m);
                                  setOpenDropdown(null);
                                }}
                                className={cn(
                                  "w-full text-left px-3 py-2.5 text-[13px] font-mono transition-colors",
                                  m === p.activeModel
                                    ? "bg-[#e7f3f1] text-[#006d67] font-semibold"
                                    : "text-[#083f3a] hover:bg-[#f4f5f5]",
                                )}
                              >
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
                    <button
                      onClick={() => {
                        setEditing(p.id);
                        setKeyVal("");
                        setShowKey(false);
                        setOpenDropdown(null);
                      }}
                      className="text-[12px] text-[#006d67] hover:underline"
                    >
                      Replace key
                    </button>
                  </div>
                )}

                {/* Connect key button */}
                {!p.connected && editing !== p.id && (
                  <button
                    onClick={() => {
                      setEditing(p.id);
                      setKeyVal("");
                      setShowKey(false);
                    }}
                    className="mt-2.5 inline-flex items-center gap-1.5 text-[13px] text-[#006d67] hover:text-[#005b56] font-medium"
                  >
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
                        placeholder={
                          p.keyPrefix ? `${p.keyPrefix}…` : "API key"
                        }
                        autoFocus
                        className="flex-1 border-none outline-none font-mono text-[13.5px] text-[#083f3a] bg-transparent py-1.5 tracking-[0.02em]"
                      />
                      <button
                        onClick={() => setShowKey((s) => !s)}
                        className="bg-black/5 text-[#083f3a]/60 text-[11.5px] font-semibold px-2.5 py-1.5 rounded-lg"
                      >
                        {showKey ? "Hide" : "Show"}
                      </button>
                    </div>
                    <div className="flex items-center gap-2 mt-2.5">
                      <button
                        disabled={!keyVal.trim()}
                        onClick={() => {
                          onSaveKey(p.id, keyVal.trim());
                          setEditing(null);
                        }}
                        className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#006d67] text-white text-[13px] font-semibold hover:bg-[#005b56] disabled:opacity-40 transition-colors"
                      >
                        <Ic path={PATHS.check} size={14} /> Save &amp; activate
                      </button>
                      <button
                        onClick={() => setEditing(null)}
                        className="text-[13px] text-[#083f3a]/50 hover:text-[#083f3a] px-2 transition-colors"
                      >
                        Cancel
                      </button>
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
            <Ic path={PATHS.key} size={13} className="text-[#006d67]" /> Web
            search
          </span>
          <p className="text-[12.5px] text-[#083f3a]/60 mt-1 mb-2.5 leading-[1.5]">
            Add a Tavily key (tavily.com) to let the assistant search the web.
            Optional.
          </p>
          <div className="border rounded-xl p-3.5 border-[#e6e4df]">
            <div className="flex items-center justify-between gap-2">
              <span className="font-semibold text-[15px]">Tavily</span>
              {tavily.connected ? (
                <span className="inline-flex items-center gap-1 text-[12px] font-semibold text-[#006d67] bg-[#006d67]/10 px-2 py-0.5 rounded-full">
                  <Ic path={PATHS.check} size={11} /> Connected
                </span>
              ) : (
                <span className="text-[12px] text-[#083f3a]/40">
                  Not connected
                </span>
              )}
            </div>

            {tavily.connected && !editingTavily && (
              <div className="flex items-center justify-between mt-2.5">
                <span className="inline-flex items-center gap-1.5 text-[12px] font-mono text-[#083f3a]/50">
                  <Ic path={PATHS.shield} size={12} /> {tavily.hint}
                </span>
                <button
                  onClick={() => {
                    setEditingTavily(true);
                    setTavilyVal("");
                  }}
                  className="text-[12px] text-[#006d67] hover:underline"
                >
                  Replace key
                </button>
              </div>
            )}

            {!tavily.connected && !editingTavily && (
              <button
                onClick={() => {
                  setEditingTavily(true);
                  setTavilyVal("");
                }}
                className="mt-2.5 inline-flex items-center gap-1.5 text-[13px] text-[#006d67] hover:text-[#005b56] font-medium"
              >
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
                    onClick={() => {
                      onSaveTavily(tavilyVal.trim());
                      setEditingTavily(false);
                    }}
                    className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#006d67] text-white text-[13px] font-semibold hover:bg-[#005b56] disabled:opacity-40 transition-colors"
                  >
                    <Ic path={PATHS.check} size={14} /> Save
                  </button>
                  <button
                    onClick={() => setEditingTavily(false)}
                    className="text-[13px] text-[#083f3a]/50 hover:text-[#083f3a] px-2 transition-colors"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>

        <p className="mt-4 flex items-center gap-1.5 text-[12px] text-[#083f3a]/40">
          <Ic path={PATHS.shield} size={12} /> Keys never leave your account and
          are encrypted at rest.
        </p>
      </div>
      <style>{`@keyframes slideUp { from { transform: translateY(34px); opacity: 0.25; } to { transform: translateY(0); opacity: 1; } }`}</style>
    </div>
  );
}

// ── End confirm dialog ─────────────────────────────────────────────────────

function EndConfirmDialog({
  remainingLabel,
  onConfirm,
  onCancel,
}: {
  remainingLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <div
      className="absolute inset-0 z-45 flex items-end bg-[rgba(8,40,38,0.42)] backdrop-blur-[2px]"
      onClick={onCancel}
    >
      <div
        className="w-full bg-white text-[#083f3a] rounded-t-[22px] px-5 pt-6 pb-5"
        onClick={(e) => e.stopPropagation()}
        style={{ animation: "slideUp 0.3s cubic-bezier(0.2,0.8,0.2,1) both" }}
      >
        <h3 className="font-['Playfair_Display'] text-[22px] font-semibold leading-[1.18] mb-2">
          End the session early?
        </h3>
        <p className="text-[13.5px] text-[#083f3a]/60 leading-[1.5] mb-4">
          {remainingLabel && `There's still ${remainingLabel} left. `}I'll do a
          short close — gather what became clear and ask whether to keep it — so
          nothing is lost.
        </p>
        <div className="flex gap-2">
          <button
            onClick={onConfirm}
            className="flex-1 py-3 rounded-xl bg-[#006d67] text-white text-[14px] font-semibold hover:bg-[#005b56] transition-colors"
          >
            End &amp; wrap up
          </button>
          <button
            onClick={onCancel}
            className="flex-1 py-3 rounded-xl border border-[#e6e4df] text-[#083f3a] text-[14px] font-medium hover:border-[#006d67]/40 transition-colors"
          >
            Keep going
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Copy button ────────────────────────────────────────────────────────────

function CopyButton({
  text,
  tone = "light",
}: {
  text: string;
  tone?: "light" | "dark";
}) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    let ok = false;
    // navigator.clipboard needs a secure context (HTTPS) — unavailable over plain
    // HTTP on the LAN, so fall back to a selection + execCommand that works on mobile.
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
        ok = true;
      }
    } catch {
      /* fall through */
    }
    if (!ok) {
      const ta = document.createElement("textarea");
      ta.value = text;
      ta.setAttribute("readonly", "");
      ta.style.position = "fixed";
      ta.style.top = "0";
      ta.style.left = "0";
      ta.style.width = "1px";
      ta.style.height = "1px";
      ta.style.opacity = "0";
      document.body.appendChild(ta);
      ta.focus();
      ta.select();
      ta.setSelectionRange(0, text.length); // iOS needs an explicit range
      try {
        document.execCommand("copy");
      } catch {
        /* ignore */
      }
      document.body.removeChild(ta);
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };
  return (
    <button
      onClick={copy}
      className={cn(
        "inline-flex items-center gap-1 self-start text-[11.5px] transition-opacity -mt-1 opacity-70 hover:opacity-100",
        tone === "dark"
          ? "text-[#083f3a]/55 hover:text-[#083f3a]"
          : "text-white/60 hover:text-white",
      )}
      title="Copy message"
    >
      <Ic path={copied ? PATHS.check : PATHS.copy} size={12} />
      {copied ? "Copied" : "Copy"}
    </button>
  );
}

// ── Composer ───────────────────────────────────────────────────────────────

function Composer({
  onSend,
  placeholder,
  busy,
  onStop,
  initialValue,
  onDraftChange,
  leftAction,
}: {
  onSend: (text: string) => void;
  placeholder: string;
  busy: boolean;
  onStop: () => void;
  /** Restores an unsent draft after a remount (e.g. the session end card
   *  temporarily replaces the composer). */
  initialValue?: string;
  onDraftChange?: (v: string) => void;
  /** Rendered inside the input pill, left of the textarea (e.g. the GROW
   *  session starter). */
  leftAction?: ReactNode;
}) {
  const [v, setV] = useState(initialValue ?? "");
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
    onDraftChange?.("");
  };

  // Layout mirrors the Claude app composer: the textarea spans the full width
  // on top; below it a bottom row with quick actions on the left (e.g. "Start
  // GROW session", like the app's "</> Code" chip) and Send/Stop on the right.
  return (
    <div className="px-3 pb-3 pt-1 sm:px-4 sm:pb-4">
      <div className="rounded-2xl border border-white/35 bg-white px-3 pt-2.5 pb-2 shadow-sm transition-[border-color,box-shadow] focus-within:border-white focus-within:ring-[3px] focus-within:ring-white/20">
        <textarea
          ref={ref}
          value={v}
          rows={1}
          placeholder={placeholder}
          onChange={(e) => {
            setV(e.target.value);
            onDraftChange?.(e.target.value);
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              fire();
            }
          }}
          className="w-full bg-transparent resize-none outline-none text-[14.5px] leading-[1.45] text-[#083f3a] placeholder:text-[#083f3a]/40 max-h-32 px-1 py-1"
        />
        <div className="flex items-center gap-2 pt-1">
          {leftAction}
          <span className="flex-1" />
          {busy ? (
            <button
              onClick={onStop}
              className="w-9 h-9 shrink-0 grid place-items-center rounded-full bg-[#006d67] text-white hover:bg-[#005b56] transition-colors"
              title="Stop"
            >
              <span className="w-3 h-3 rounded-sm bg-white" />
            </button>
          ) : (
            <button
              onClick={fire}
              disabled={!v.trim()}
              className="w-9 h-9 shrink-0 grid place-items-center rounded-full bg-[#006d67] text-white disabled:opacity-40 hover:bg-[#005b56] transition-colors"
              title="Send"
            >
              <ArrowUp className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

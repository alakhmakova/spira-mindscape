import {
  useEffect,
  useRef,
  useState,
  useCallback,
  type ReactNode,
} from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { SheetHead } from "@/components/spira/SheetHead";
import { X, ArrowUp, Paperclip } from "@/components/spira/icons";
import {
  Drawer,
  DrawerContent,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { useNavigate } from "@tanstack/react-router";
import { useIsMobile } from "@/hooks/use-mobile";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";
import { useAi } from "./ai-store";
import { useSpira } from "@/lib/spira/store";
import { useActivityGate } from "@/lib/useActivityGate";
import { logger } from "@/lib/logger";
import { cn } from "@/lib/utils";
import { SproutArt } from "@/components/spira/SproutArt";
import { ResourceAttachSheet } from "./ResourceAttachSheet";
import { NoticeCard } from "@/components/spira/Notice";
import {
  clearComposerDraft,
  loadComposerDraft,
  saveComposerDraft,
} from "./composer-draft";
import type { AiAction, Goal, Resource } from "@/lib/spira/types";
// Only the option type survives: the chat's messages are no longer sonner toasts (they are
// the panel's own card, above the field), but the call sites still pass sonner-shaped opts.
import { type ExternalToast } from "sonner";
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
  getTranscript,
  getTranscriptRevision,
  putTranscript,
  deleteTranscript,
  getAiProvider,
  saveAiProvider,
  type HistoryEntry,
  type ChatAttachment,
} from "./ai-api";
import { resourceTypeMeta } from "@/components/spira/resource-meta";
import {
  type ProposalKind,
  type Proposal,
  uid,
  stripHtml,
  fmtDeadline,
  dedupCreates,
  isOptionActivate,
  buildHistory,
  createAspects,
  createSummary,
  editDisplay,
  proposalContext,
  applyExcludedAspects,
  proposalFromToolArgs,
} from "./proposal-logic";

// Chat toasts are positioned per device: on MOBILE the assistant is a bottom
// sheet whose composer sits at the bottom, so top-center is clearest; on DESKTOP
// the panel is a left column and the bottom-right corner (sonner's default) reads
// best. Decided per call from the current viewport (< 768px = mobile, matching
// `useIsMobile`). Scoped to this panel; toasts elsewhere keep their default.
/**
 * **A message from the chat belongs to the chat.**
 *
 * These used to be sonner toasts, positioned against the *viewport* — top-centre on a phone,
 * bottom-right on a laptop — so a confirmation of something done inside the panel appeared as far
 * from the panel as the screen allowed, at whatever width sonner felt like. The owner asked for
 * one rule on both surfaces (2026-08-24): **directly above the message field, exactly as wide as
 * it**, so it is next to the thing that caused it and is the same object on a narrow phone and a
 * wide panel.
 *
 * The call sites did not change — there are about sixty of them — so `chatToast` stays, and only
 * where it delivers to is different: a tiny bus the panel subscribes to and renders as the app's
 * one `NoticeCard`. Android's twin is `ChatToast` in `AiChatScreen.kt`.
 */
type PanelNotice = { id: number; kind: "success" | "error"; message: string };

let panelNoticeSeq = 0;
const panelNoticeListeners = new Set<(n: PanelNotice) => void>();

const emitPanelNotice = (kind: PanelNotice["kind"], message: string) => {
  const notice = { id: ++panelNoticeSeq, kind, message };
  panelNoticeListeners.forEach((fn) => fn(notice));
};

/** How long a chat toast stays before it takes itself away. */
const PANEL_NOTICE_MS = 6000;

const chatToast = {
  success: (message: string, _opts?: ExternalToast) =>
    emitPanelNotice("success", message),
  error: (message: string, _opts?: ExternalToast) =>
    emitPanelNotice("error", message),
};

// ── Types ─────────────────────────────────────────────────────────────────

type Msg = {
  id: string;
  role: "user" | "assistant" | "system" | "end" | "closed";
  content: string;
  streaming?: boolean;
  proposals?: Proposal[];
  error?: boolean; // an error bubble — rendered with a warning icon, excluded from history
  /** Ephemeral progress line (GROW library indexing). Display-only: never part
   *  of content, so it can't leak into the transcript or the model history. */
  status?: string;
  /** Files attached to this (user) message — shown as chips; not persisted. */
  attachments?: ChatAttachment[];
  /** Set on a user message that came from a card's "Edit" box: the headline of the card
   *  being revised, shown as a caption above the bubble so the request is traceable. */
  revisedLabel?: string;
};

/**
 * How far past the planned end the coach may run before the app insists. The
 * coach owns the ending, but a model that never calls `end_session` would leave
 * the session open forever — so at this point it is TOLD to wrap up. It still
 * writes the record and decides the proposals: the backstop triggers the
 * analysis, it does not replace it.
 */
const OVERRUN_GRACE_SECONDS = 10 * 60;

/**
 * The ending is a sequence, not a single card (owner, 2026-08-22): the coach
 * calls `end_session`, then the user decides on the session record, then on
 * anything proposed for the goal, and only then does the coach say goodbye.
 * `grow-end` is the record card, `grow-review` the proposals, `grow-farewell`
 * the goodbye.
 */
type Mode =
  | "chat"
  | "grow-start"
  | "grow-active"
  | "grow-closing"
  | "grow-end"
  | "grow-review"
  | "grow-farewell";

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
    id: "COHERE",
    vendor: "Cohere",
    context: "256 000 tokens",
    connected: false,
    // Cohere keys carry no distinguishing prefix, so there is nothing to check for.
    keyPrefix: "",
    // Pinned ids, because Cohere publishes no "-latest" alias to hide behind. The live list
    // (GET /v1/models?endpoint=chat) replaces these as soon as it loads; this is only what is
    // shown before it does. Command A is the one Cohere's own rate-limit page puts at 500
    // requests a minute, where the newest variants are "contact sales".
    activeModel: "command-a-03-2025",
    models: [
      "command-a-03-2025",
      "command-r-plus-08-2024",
      "command-r-08-2024",
    ],
  },
  {
    id: "GEMINI",
    vendor: "Google Gemini",
    context: "1 000 000 tokens",
    connected: false,
    keyPrefix: "AIza",
    // Aliases, not pinned versions (BUG-059). This list is the fallback shown before the
    // live one loads from the provider, and it had gone stale: Google retired
    // gemini-2.5-flash for new keys ("no longer available to new users"), and 2.0/1.5 with
    // it, so three of the four offered here could not send a message. Google maintains
    // "-latest" as a pointer at the current model, so these cannot rot the same way.
    activeModel: "gemini-flash-lite-latest",
    models: [
      "gemini-flash-lite-latest",
      "gemini-flash-latest",
      "gemini-pro-latest",
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
    return parseTranscript(raw) ?? [];
  } catch {
    return [];
  }
}

/** Parses a stored transcript JSON string into messages, or null if unusable. */
function parseTranscript(content: string | null | undefined): Msg[] | null {
  if (!content) return null;
  try {
    const parsed = JSON.parse(content) as Msg[];
    return Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

/**
 * The messages to persist: settled only (no in-flight streaming placeholder),
 * capped, with attachment file bytes stripped — only names/labels survive, so a
 * stored transcript stays small (localStorage quota + the synced server blob).
 */
function messagesForStore(msgs: Msg[]): Msg[] {
  return msgs
    .filter((m) => !m.streaming)
    .slice(-CHAT_MAX_MESSAGES)
    .map((m) =>
      m.attachments?.length
        ? {
            ...m,
            attachments: m.attachments.map((a) => ({ ...a, dataUrl: "" })),
          }
        : m,
    );
}

/**
 * Writes the transcript to localStorage and returns the JSON that was stored, so
 * the caller can also push it to the server (cross-device sync) without
 * re-serialising.
 */
/**
 * Carries local attachment bytes (`dataUrl`) into a transcript adopted from the server. The stored
 * / synced transcript strips file bytes (see {@link messagesForStore}), so when the cross-device
 * poll adopts the server copy it would otherwise blank out an image we still hold in memory — and
 * an image chip only previews while its bytes are present. Match by message id + attachment index
 * (guarded by name) and keep the local bytes wherever the incoming copy has none.
 */
function mergeAttachmentBytes(prev: Msg[], next: Msg[]): Msg[] {
  const prevById = new Map(prev.map((m) => [m.id, m]));
  return next.map((m) => {
    if (!m.attachments?.length) return m;
    const old = prevById.get(m.id);
    if (!old?.attachments?.length) return m;
    return {
      ...m,
      attachments: m.attachments.map((a, i) => {
        if (a.dataUrl) return a;
        const prevA = old.attachments![i];
        return prevA?.dataUrl && prevA.name === a.name
          ? { ...a, dataUrl: prevA.dataUrl }
          : a;
      }),
    };
  });
}

function saveTranscript(scopeKey: string, msgs: Msg[]): string {
  const settled = messagesForStore(msgs);
  const json = JSON.stringify(settled);
  if (typeof window !== "undefined") {
    try {
      if (settled.length === 0) window.localStorage.removeItem(scopeKey);
      else window.localStorage.setItem(scopeKey, json);
    } catch {
      /* quota or serialization error — non-fatal; the server copy still syncs */
    }
  }
  return json;
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

/**
 * What clicking an attachment chip does — **one rule, used by the composer's chips and by a
 * sent message's chips**, so an attachment behaves the same before and after it is sent.
 *
 * It exists because the web only ever opened images: `mime.startsWith("image/") && dataUrl`.
 * A resource chip carries neither (its whole point is that the bytes stay on the server), so
 * every note, link and contact the user attached was inert — while Android opened all of them
 * through `LocalOpenAttachment`. Found by the owner, 2026-08-23.
 *
 * Per kind:
 *
 * | Attachment | Click |
 * |---|---|
 * | An image with bytes in hand | the image preview |
 * | A **link** resource | opens the URL in a new tab (owner's call — a preview of a web page is just a worse browser) |
 * | A **note** resource | its body, as HTML, in the content modal |
 * | An **email** resource | the contact's details |
 * | A **file** resource | nothing, deliberately, for now |
 *
 * Returns `null` when there is nothing to open, and the chip then renders as plain text
 * rather than as a button that does nothing.
 */
function attachmentOpener(
  a: ChatAttachment,
  resources: Resource[] | undefined,
  showContent: (c: {
    title: string;
    body: string;
    html?: boolean;
    image?: string;
  }) => void,
): (() => void) | null {
  if (a.mime?.startsWith("image/") && a.dataUrl) {
    return () => showContent({ title: a.name, body: "", image: a.dataUrl });
  }
  if (a.resourceId == null) return null;

  const r = resources?.find((x) => Number(x.id) === a.resourceId);
  if (!r) return null;

  switch (r.type) {
    case "link":
      return () => window.open(r.url, "_blank", "noopener,noreferrer");
    case "note":
      return () => showContent({ title: r.title, body: r.body, html: true });
    case "email":
      return () =>
        showContent({
          title: r.name,
          body:
            [r.role, r.email, r.phone].filter(Boolean).join(", ") ||
            "No details saved.",
        });
    default:
      // A file: no preview yet (owner, 2026-08-23).
      return null;
  }
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
        {/* A chat has no natural content height — a two-message conversation would make a
            two-message-tall drawer — so unlike the form sheets this one cannot be
            content-sized. `sheet-h-92` gives it a real one, measured from the KEYBOARD-FREE
            viewport rather than from `vh`: with `interactive-widget=resizes-content` the
            keyboard shrinks the layout viewport, so `92vh` meant "92 % of the sliver above the
            keyboard" — 276 px of an 888 px phone. See CLAUDE.md → Sheets → the height. */}
        <DrawerContent className="sheet-h-92 mt-0 flex flex-col px-0 border-0 bg-[#0A8080] text-white">
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
        // The coach sits on the RIGHT of the page (owner, 2026-08-23) — the left column is
        // the standing navigation now. Hence the border and the shadow fall the other way.
        "sticky top-0 z-40 hidden h-screen max-h-screen shrink-0 flex-col border-l border-white/15 bg-[#0A8080] text-white shadow-[-12px_0_30px_-24px_rgba(0,0,0,0.55)] md:flex",
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
    // When set, the modal shows this image (a data: URL) instead of text — used to
    // preview an image file attached to a chat message.
    image?: string;
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
      chatToast.error(syncError);
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
        chatToast.success("Goal created");
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
            chatToast.success("Goal confidence updated");
          }
        } else if (p.field === "deadline") {
          updateGoal(p.goalId, { deadline: normalizeDeadline(v) });
          chatToast.success("Goal deadline updated");
        } else {
          updateGoal(p.goalId, { title: (v ?? "").trim().slice(0, 200) });
          chatToast.success("Goal renamed");
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
        else chatToast.error("I couldn't find that goal to delete.");
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
          chatToast.error(
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
            chatToast.success("Goal updated");
          }
          break;
        case "confidence": {
          const c = parseInt(p.rawValue ?? p.title);
          if (c >= 1 && c <= 10) {
            updateGoal(goal.id, {
              confidence: c as import("@/lib/spira/types").Confidence,
            });
            chatToast.success("Confidence updated");
          }
          break;
        }
        case "deadline": {
          const iso = normalizeDeadline(p.rawValue || p.title);
          updateGoal(goal.id, { deadline: iso });
          chatToast.success("Deadline updated");
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
            chatToast.success("Checklist added");
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
            chatToast.success("Target added");
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
            chatToast.success(
              p.done ? "Target added & completed" : "Target added",
            );
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
          chatToast.success(
            p.done ? "Option added & selected" : "Option added",
          );
          break;
        case "obstacle":
          addReality(goal.id, "obstacles", p.title);
          chatToast.success("Obstacle added");
          break;
        case "action":
          addReality(goal.id, "actions", p.title);
          chatToast.success("Action added");
          break;
        case "note":
          addResource(goal.id, {
            type: "note",
            title: label(p.title),
            body: p.body ?? "",
          });
          chatToast.success("Note saved");
          break;
        case "link": {
          const url = (p.patch?.url ?? "").trim();
          // A link resource needs a web address. Don't fail silently (which looked like
          // "the card did nothing") — tell the user. To rename an existing link the AI must
          // use edit_link with its id, not create a new one.
          if (!url) {
            chatToast.error(
              "A link needs a web address (URL). To rename an existing link, ask me to edit it.",
            );
            break;
          }
          // Empty title is intentional — the backend derives a label from the domain.
          const linkTitle = (p.patch?.title ?? "").trim().slice(0, 200);
          addResource(goal.id, { type: "link", title: linkTitle, url });
          chatToast.success("Link added");
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
          chatToast.success("Contact added");
          break;
        }

        // ── edit existing items ──
        case "edit_target": {
          if (!p.itemId) break;
          // Text is required — never let an "edit" blank it out (the AI must use delete_* to
          // remove things, not erase the text).
          if (!p.title.trim()) {
            chatToast.error("A target needs a name — use delete to remove it.");
            break;
          }
          const iso = normalizeDeadline(p.deadline);
          updateTarget(goal.id, p.itemId, {
            title: p.title,
            ...(iso ? { deadline: iso } : {}),
          });
          chatToast.success("Target updated");
          break;
        }
        case "edit_option":
          if (!p.itemId) break;
          if (!p.title.trim()) {
            chatToast.error("An option needs text — use delete to remove it.");
            break;
          }
          updateOption(goal.id, p.itemId, { text: p.title });
          chatToast.success("Option updated");
          break;
        case "edit_obstacle":
          if (!p.itemId) break;
          if (!p.title.trim()) {
            chatToast.error(
              "An obstacle needs text — use delete to remove it.",
            );
            break;
          }
          updateReality(goal.id, "obstacles", p.itemId, p.title);
          chatToast.success("Obstacle updated");
          break;
        case "edit_action":
          if (!p.itemId) break;
          if (!p.title.trim()) {
            chatToast.error("An action needs text — use delete to remove it.");
            break;
          }
          updateReality(goal.id, "actions", p.itemId, p.title);
          chatToast.success("Action updated");
          break;
        case "edit_note":
          if (p.itemId) {
            updateResource(goal.id, p.itemId, {
              title: label(p.title),
              body: p.body ?? "",
            });
            chatToast.success("Note updated");
          }
          break;
        case "edit_link":
          if (p.itemId && p.patch && Object.keys(p.patch).length) {
            updateResource(
              goal.id,
              p.itemId,
              p.patch as Partial<import("@/lib/spira/types").Resource>,
            );
            chatToast.success("Link updated");
          }
          break;
        case "edit_email":
          if (p.itemId && p.patch && Object.keys(p.patch).length) {
            updateResource(
              goal.id,
              p.itemId,
              p.patch as Partial<import("@/lib/spira/types").Resource>,
            );
            chatToast.success("Contact updated");
          }
          break;

        // ── state changes ──
        case "complete_target":
          if (p.itemId) {
            updateTarget(goal.id, p.itemId, { done: p.done !== false });
            chatToast.success("Target updated");
          }
          break;
        case "target_progress": {
          if (!p.itemId) break;
          const n = Number(p.rawValue ?? p.title);
          if (!Number.isNaN(n)) {
            updateTarget(goal.id, p.itemId, { current: n });
            chatToast.success("Progress updated");
          }
          break;
        }
        case "select_option":
          if (p.itemId) {
            selectOption(goal.id, p.itemId);
            chatToast.success("Option selected");
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
            chatToast.success("Checklist updated");
          }
          break;
        }
        case "add_checklist_item": {
          if (!p.itemId) break;
          const parent = goal.targets.find((t) => t.id === p.itemId);
          if (!parent || parent.type !== "checklist") {
            chatToast.error(
              "Sub-tasks can only be added to a checklist target",
            );
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
          chatToast.success("Sub-task added");
          break;
        }

        // ── delete smaller items (the card's Accept is the confirmation) ──
        case "delete_option":
          if (p.itemId && goal.options.some((o) => o.id === p.itemId)) {
            removeOption(goal.id, p.itemId);
            chatToast.success("Option deleted");
          } else chatToast.error("I couldn't find that option to delete.");
          break;
        case "delete_obstacle":
          if (
            p.itemId &&
            goal.reality.obstacles.some((o) => o.id === p.itemId)
          ) {
            removeReality(goal.id, "obstacles", p.itemId);
            chatToast.success("Obstacle deleted");
          } else chatToast.error("I couldn't find that obstacle to delete.");
          break;
        case "delete_action":
          if (p.itemId && goal.reality.actions.some((a) => a.id === p.itemId)) {
            removeReality(goal.id, "actions", p.itemId);
            chatToast.success("Action deleted");
          } else chatToast.error("I couldn't find that action to delete.");
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
            chatToast.success("Sub-task deleted");
          } else
            chatToast.error("I couldn't find that checklist item to delete.");
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
        chatToast.success("Goal created");
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
  // Proposals arrive in the same turn as `end_session`, but must not be shown
  // until the user has dealt with the session record — so they wait here.
  const [heldProposals, setHeldProposals] = useState<Proposal[]>([]);
  // The chat's own toast — see `chatToast`. One at a time: a second message replaces the first
  // rather than stacking a column of cards over the field.
  const [notice, setNotice] = useState<PanelNotice | null>(null);

  useEffect(() => {
    const listener = (n: PanelNotice) => setNotice(n);
    panelNoticeListeners.add(listener);
    return () => {
      panelNoticeListeners.delete(listener);
    };
  }, []);

  // Keyed on the notice's id, so a second message restarts the clock instead of inheriting
  // whatever was left of the first one's.
  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(null), PANEL_NOTICE_MS);
    return () => clearTimeout(t);
  }, [notice]);
  // How the session ended, which decides what the coach is told when asked for
  // the goodbye and whether the record may claim the session completed.
  const endKindRef = useRef<"complete" | "early" | "overrun">("complete");
  // Whether the record was actually saved — the goodbye is told, and the
  // closing system note reports it.
  const memorySavedRef = useRef(false);
  // True while the final goodbye turn is in flight, so its completion exits
  // the session instead of being treated as an ordinary reply.
  const goodbyeRef = useRef(false);
  const [providers, setProviders] = useState<ProviderInfo[]>(PROVIDERS_DEFAULT);
  const [activeProv, setActiveProv] = useState(
    () => readSavedProvider() || "ANTHROPIC",
  );
  const [tavily, setTavily] = useState<{ connected: boolean; hint?: string }>({
    connected: false,
  });

  const stopRef = useRef(false);
  // Cross-device transcript sync (BUG-018): true while we're pulling the server's
  // copy for a scope, so the persist effect doesn't push a stale local copy back
  // and clobber a newer conversation from another device. Starts true so the very
  // first mount waits for the server before any push.
  const hydratingRef = useRef(true);
  // The next persist should NOT push to the server (used by "New chat", which
  // deletes the server row — the resulting empty state must not re-create it,
  // and by adopting a polled server copy — which must not echo back).
  const skipServerPutRef = useRef(false);
  // The server `updatedAt` we last saw/wrote, so the poll only adopts a copy that
  // is genuinely newer (from another device), never re-adopting our own writes.
  const lastSeenUpdatedRef = useRef<string | null>(null);
  // Latest `busy` for async callbacks (a resolved server fetch must see the
  // current value, not the one captured when the fetch started).
  const busyRef = useRef(busy);
  useEffect(() => {
    busyRef.current = busy;
  }, [busy]);
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
    mode === "grow-active" ||
    mode === "grow-closing" ||
    mode === "grow-end" ||
    mode === "grow-review" ||
    mode === "grow-farewell";
  const list = inGrow ? gmsgs : msgs;
  // Ending early is only on offer while the session is really running: not
  // mid-stream (the wrap-up request would be dropped and the button would look
  // dead), and not once the ending sequence has begun — it is already ending.
  const canEndEarly =
    !busy && (mode === "grow-active" || mode === "grow-closing");

  // A pending proposal card IS the input — it renders in the footer (where the
  // composer would be) instead of inline, so it sits right above the keyboard.
  const pendingMsg = list.find((m) =>
    m.proposals?.some((pr) => pr.status === "pending"),
  );

  // Load saved keys on mount
  useEffect(() => {
    // Load the configured keys AND the user's server-saved provider together, so
    // the active provider follows the user across devices (BUG-018 follow-up).
    Promise.all([listApiKeys(), getAiProvider()])
      .then(
        ([keys, serverProvider]: [
          Array<{ provider: string; hint: string; model: string }>,
          string | null,
        ]) => {
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
          // Active chat provider must be an LLM with a key (never Tavily — a
          // search key). Prefer the SERVER-saved choice (cross-device), then this
          // device's last choice, then the first available LLM key.
          const hasKey = (p?: string | null) =>
            !!p && p !== "TAVILY" && keys.some((k) => k.provider === p);
          const local = readSavedProvider();
          const chosen = hasKey(serverProvider)
            ? serverProvider
            : hasKey(local)
              ? local
              : keys.find((k) => k.provider !== "TAVILY")?.provider;
          if (chosen) {
            setActiveProv(chosen);
            saveActiveProvider(chosen); // refresh this device's local cache
          }
        },
      )
      .catch((err) => {
        // Fails legitimately when logged out or the backend is down — deliberately
        // not reported, or every offline page load would produce one.
        logger.debug("Initial AI key/provider fetch failed", err);
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

  // Cross-device sync (BUG-018): pull the server's transcript for this scope on
  // mount and whenever the scope changes. The server is the shared source of
  // truth (last write wins). If the server has nothing for this scope yet, seed
  // it from this device's local history so another device can pick it up. GROW
  // is ephemeral and never synced.
  useEffect(() => {
    if (inGrow) return;
    const goalId = context.goalId;
    const scopeAtFetch = scopeKey;
    hydratingRef.current = true;
    let cancelled = false;
    getTranscript(goalId)
      .then((server) => {
        if (cancelled || scopeAtFetch !== chatScopeKey(context.goalId)) return;
        if (busyRef.current) return; // don't stomp a send the user just started
        lastSeenUpdatedRef.current = server?.updatedAt ?? null;
        const serverMsgs = parseTranscript(server?.content);
        if (serverMsgs && serverMsgs.length > 0) {
          skipServerPutRef.current = true; // adopting must not echo back to the server
          setMsgs(serverMsgs);
          saveTranscript(scopeAtFetch, serverMsgs); // refresh the local cache
        } else {
          const local = loadTranscript(scopeAtFetch);
          if (local.length > 0)
            putTranscript(goalId, JSON.stringify(messagesForStore(local))).then(
              (ts) => {
                if (ts) lastSeenUpdatedRef.current = ts; // record our own seed
              },
            );
        }
      })
      .finally(() => {
        if (!cancelled) hydratingRef.current = false;
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scopeKey]);

  // Persist regular chat after each settled turn. Skipping while `busy` avoids
  // a localStorage write per streamed token (the streaming placeholder is
  // excluded from storage anyway). Deps intentionally exclude scopeKey: on a
  // scope switch the reload effect sets msgs and this effect then runs with the
  // new scopeKey captured — so we never write one scope's messages into another.
  useEffect(() => {
    if (busy) return;
    const json = saveTranscript(scopeKey, msgs);
    // Push to the server for cross-device sync — but not while hydrating (would
    // clobber a newer copy from another device) or right after "New chat"
    // deleted the server row (the resulting empty state must not re-create it).
    if (inGrow || hydratingRef.current) return;
    if (skipServerPutRef.current) {
      skipServerPutRef.current = false;
      return;
    }
    putTranscript(context.goalId, json).then((ts) => {
      if (ts) lastSeenUpdatedRef.current = ts; // record our own write
    });
  }, [msgs, busy]); // eslint-disable-line react-hooks/exhaustive-deps

  // Near-real-time cross-device sync: while the panel is open, poll the server
  // for this scope and adopt a copy that is genuinely newer (another device sent
  // a turn). This component only mounts while the panel is open, so the interval
  // is naturally scoped to when the chat is visible. Turn-based updates make a
  // few-second poll effectively live without websocket infrastructure. Guards:
  // never adopt mid-stream (`busy`) or during hydration, skip background tabs,
  // and set `skipServerPutRef` so an adopted copy isn't echoed back (no ping-pong).
  //
  // Cost guard (mirrors the goals poll in AppShell via useActivityGate). Two layers:
  //   1. Each tick asks only for the transcript's `updatedAt` (`getTranscriptRevision`) and
  //      fetches the conversation itself just when that moved. Comparing timestamps used to
  //      happen after the whole transcript was already down the wire, so an idle-but-open chat
  //      spent metered (Neon) egress on its own history every few seconds.
  //   2. The gate still pauses polling after a few minutes without user interaction
  //      (pointer/key/wheel/touch — reading-by-scroll counts) and, on resume, replays the latest
  //      poll at once. Trade-off: while idle, a message sent from ANOTHER device isn't adopted
  //      here until you interact — acceptable for a personal app, and focus/visibility elsewhere
  //      still refresh.
  const transcriptPollRef = useRef<() => void>(() => {});
  const isChatActive = useActivityGate(3 * 60_000, () =>
    transcriptPollRef.current(),
  );
  useEffect(() => {
    if (inGrow) return;
    let cancelled = false;
    const poll = async () => {
      if (cancelled || busyRef.current || hydratingRef.current) return;
      if (
        typeof document !== "undefined" &&
        document.visibilityState !== "visible"
      )
        return;
      if (!isChatActive()) return; // idle: let the DB rest
      const scopeAtPoll = scopeKey;
      // Cheap first: a timestamp, not the conversation. `undefined` means the check itself
      // failed, so fall through and fetch rather than assume nothing changed.
      const revision = await getTranscriptRevision(context.goalId);
      if (cancelled || revision === null) return; // null: nothing stored for this scope
      if (revision !== undefined && revision === lastSeenUpdatedRef.current)
        return;
      const server = await getTranscript(context.goalId);
      if (
        cancelled ||
        !server ||
        !server.updatedAt ||
        scopeAtPoll !== chatScopeKey(context.goalId) ||
        busyRef.current ||
        hydratingRef.current
      )
        return;
      if (server.updatedAt === lastSeenUpdatedRef.current) return; // nothing new
      const serverMsgs = parseTranscript(server.content);
      if (!serverMsgs) return;
      lastSeenUpdatedRef.current = server.updatedAt;
      skipServerPutRef.current = true; // adopting must not echo back
      // Keep any local image bytes the server copy dropped, so an image attached this session
      // still previews after a sync tick adopts the (stripped) server transcript.
      setMsgs((prev) => mergeAttachmentBytes(prev, serverMsgs));
      saveTranscript(scopeAtPoll, serverMsgs);
    };
    transcriptPollRef.current = () => void poll();
    // 10s rather than the original 4s: the tick is now a timestamp, and a few extra seconds of
    // cross-device lag is a fair trade for two thirds fewer requests.
    const interval = window.setInterval(poll, 10_000);
    return () => {
      cancelled = true;
      transcriptPollRef.current = () => {};
      window.clearInterval(interval);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scopeKey, inGrow, isChatActive]);

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
      .catch((err) => {
        // Best-effort — the chat works without it, but consistent failure means pending
        // proposals silently stop coming back, which is worth noticing while developing.
        logger.warn("Restoring pending proposals failed", err);
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
    // The empty state that follows must NOT re-push to the server — we're
    // deleting the row, and cross-device sync should clear it everywhere.
    skipServerPutRef.current = true;
    setMsgs([]);
    try {
      window.localStorage.removeItem(scopeKey);
    } catch {
      /* ignore */
    }
    deleteTranscript(context.goalId);
    // No toast (owner, 2026-08-23): the emptied chat is its own confirmation, and a
    // message about it was one more thing to dismiss. Android does the same.
  };

  // ── regular chat ─────────────────────────────────────────────────────────

  const sendChat = (text: string, attachments?: ChatAttachment[]) => {
    if (busy) return;
    setMsgs((p) => [
      ...p,
      {
        id: uid(),
        role: "user",
        content: text,
        ...(attachments?.length ? { attachments } : {}),
      },
    ]);
    setBusy(true);
    stopRef.current = false;

    // Only real conversation turns belong in history — drop system notices,
    // GROW end-cards, and empty placeholders (Anthropic rejects empty content).
    const history: HistoryEntry[] = buildHistory(msgs);

    const id = uid();
    setMsgs((p) => [
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
      sessionType: "chat",
      attachments,
      onToken: (tok) => {
        if (stopRef.current) return;
        accumulated += tok;
        setMsgs((p) =>
          p.map((m) => (m.id === id ? { ...m, content: accumulated } : m)),
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
            // Cleanup of a superseded duplicate: cosmetic server-side leftover, so a
            // dev-console note is enough — not worth a report.
            if (pp.serverId != null)
              rejectProposal(pp.serverId).catch((err) =>
                logger.warn("Dropping superseded proposal failed", err),
              );
          });
        const others = afterDeletes.filter((pp) => !CREATE_KINDS.has(pp.kind));
        const finalProposals = [...others, ...creates];
        const content =
          accumulated.trim() ||
          (finalProposals.length
            ? "I've prepared this for your review."
            : // Safety net: the backend already streams a fallback, but never leave
              // an empty assistant bubble ("no response") if a turn returns nothing.
              "I didn't get a response that time — please try again.");
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
                }
              : m,
          ),
        );
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
        chatToast.error(msg);
      },
    });
  };

  const stopStream = () => {
    stopRef.current = true;
    setBusy(false);
  };

  // Revise a proposal "in place": when the user types a change on a card ("Type a change for
  // the AI…"), DON'T spawn a new card. Re-ask the model, then swap the new proposal into the
  // SAME message slot (keeping its id) so the original card simply updates — never a pile of
  // duplicate cards.
  //
  // The request itself IS written to the transcript (a user bubble captioned with the card's
  // name, then the AI's reply): the user can see what they asked for, and — because the
  // transcript is the model's history — a later revise still sees the earlier requests. The
  // model is also given the WHOLE current proposal (`proposalContext`), not the card's clipped
  // headline, so an earlier change can't be dropped just because the prompt never showed it.
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
    /** A failed revise reads as a bubble in the conversation, like a failed chat turn — the
     *  user's request is visible above it, so a silent toast would leave it unanswered. */
    const failWith = (text: string) =>
      setList((ms) => [
        ...ms,
        { id: uid(), role: "assistant" as const, content: text, error: true },
      ]);

    // The request goes into the transcript straight away — before the answer, and whatever
    // the answer turns out to be.
    setList((ms) => [
      ...ms,
      {
        id: uid(),
        role: "user" as const,
        content: instruction,
        revisedLabel: headline,
      },
    ]);

    // Safety net: a revise must never leave the card frozen with no way out. If the stream
    // never completes, recover automatically.
    const timer = setTimeout(() => {
      if (reviseTokenRef.current !== token) return;
      reviseTokenRef.current++;
      finish();
      failWith("The AI took too long — the card is unchanged. Try again.");
    }, 90_000);
    const stillActive = () =>
      reviseTokenRef.current === token && !stopRef.current;

    // The old server-side proposal row is superseded — drop it (the new one gets its own id).
    // A failure here orphans that row, so it is worth seeing even though the UI moves on.
    if (p.serverId != null)
      rejectProposal(p.serverId).catch((err) =>
        logger.reportError(err, { kind: "api" }),
      );

    // Earlier requests on this card are ordinary turns in here now, so the model sees them.
    const history: HistoryEntry[] = buildHistory(curList);

    const label = (KIND_META[p.kind]?.label ?? "change").toLowerCase();
    // A re-proposal REPLACES the old one, so the model must repeat what it isn't changing —
    // anything it leaves out is something the user silently loses.
    const message =
      `Revise the ${label} you proposed. Keep everything the user has already asked for and ` +
      `apply only the new change on top of it.\n\nCurrent proposal:\n${proposalContext(p)}\n\n` +
      `New change: ${instruction}\n\n` +
      `Re-propose it with the change applied — one proposal, complete: repeat every field you ` +
      `are not changing.`;

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
        // Close the exchange with a reply, so the user's request isn't left hanging and the
        // transcript stays a real conversation (the model's own words when it wrote any).
        const reply =
          accumulated.trim() ||
          `Updated «${proposalDisplay(replaced, goal).headline}».`;
        setList((ms) => [
          ...ms.map((m) => {
            if (m.id !== targetMsgId) return m;
            const nextProposals = (m.proposals ?? []).flatMap((pr) =>
              pr.id === targetProposalId ? [replaced, ...extras] : [pr],
            );
            return { ...m, proposals: nextProposals };
          }),
          { id: uid(), role: "assistant" as const, content: reply },
        ]);
        finish();
      },
      onError: (err) => {
        clearTimeout(timer);
        if (reviseTokenRef.current !== token) return;
        finish();
        failWith(
          err === "NETWORK"
            ? "Backend unreachable — is it running?"
            : err || "AI error. Try again.",
        );
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
      chatToast.error(
        "Finish the previous session first — save or discard its result below.",
      );
      return;
    }
    setGmsgs([]);
    endedRef.current = false;
    wrapUpRef.current = false;
    goodbyeRef.current = false;
    endKindRef.current = "complete";
    memorySavedRef.current = false;
    setHeldProposals([]);
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
        chatToast.error(err || "AI error.");
      },
    });
  };

  /**
   * One GROW turn. `wrapUp` is the timer-driven closing turn: the instruction
   * is sent to the model but never shown or kept as a user bubble, and once
   * the coach's goodbye lands the end card follows.
   */
  const sendGrow = (
    text: string,
    opts?: { wrapUp?: boolean; goodbye?: boolean },
  ) => {
    const wrapUp = opts?.wrapUp ?? false;
    const goodbye = opts?.goodbye ?? false;
    if (busy && !wrapUp) return;
    if (!wrapUp) {
      const userMsg = { id: uid(), role: "user" as const, content: text };
      setGmsgs((p) => [...p, userMsg]);
    }
    setBusy(true);
    stopRef.current = false;

    const history: HistoryEntry[] = buildHistory(gmsgs);

    const id = uid();
    setGmsgs((p) => [
      ...p,
      { id, role: "assistant", content: "", streaming: true },
    ]);
    let accumulated = "";
    const pendingProposals: Proposal[] = [];
    // Set when the coach calls `end_session` during this turn. Its summary is
    // the session record — a different text from the goodbye, which comes later.
    let endRecord: string | null = null;

    streamChat({
      goalId: context.goalId,
      message: text,
      history,
      provider: activeProv,
      sessionType: "grow",
      sessionTotalMinutes: session?.mins,
      // A wrap-up turn reports zero whatever the clock says: the coach is being
      // asked to close, and "there is room to explore" would argue against it.
      sessionRemainingSeconds: wrapUp ? 0 : Math.round(session?.remaining ?? 0),
      onSessionEnd: (argsJson) => {
        try {
          const parsed = JSON.parse(argsJson) as { summary?: unknown };
          if (typeof parsed.summary === "string" && parsed.summary.trim())
            endRecord = parsed.summary.trim();
          else endRecord = "";
        } catch {
          // A malformed payload still means the coach ended the session; the
          // record is simply empty, and the card says so rather than vanishing.
          endRecord = "";
        }
      },
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
            // Cleanup of a superseded duplicate: cosmetic server-side leftover, so a
            // dev-console note is enough — not worth a report.
            if (pp.serverId != null)
              rejectProposal(pp.serverId).catch((err) =>
                logger.warn("Dropping superseded proposal failed", err),
              );
          });
        const others = afterDeletes.filter((pp) => !CREATE_KINDS.has(pp.kind));
        const finalProposals = [...others, ...creates];
        const content =
          accumulated.trim() ||
          (finalProposals.length
            ? "I've prepared this for your review."
            : // Safety net: the backend already streams a fallback, but never leave
              // an empty assistant bubble ("no response") if a turn returns nothing.
              "I didn't get a response that time — please try again.");
        const ending = endRecord !== null;
        setGmsgs((p) =>
          p.map((m) =>
            m.id === id
              ? {
                  ...m,
                  streaming: false,
                  status: undefined,
                  content,
                  // On the ending turn the cards wait: the record is decided first.
                  ...(finalProposals.length && !ending
                    ? { proposals: finalProposals }
                    : {}),
                }
              : m,
          ),
        );
        setBusy(false);
        if (goodbye) {
          // Don't exit yet — leaving now would wipe the goodbye off the screen
          // the moment it arrived. The user closes when they've read it.
          goodbyeRef.current = false;
          setGmsgs((p) => [...p, { id: uid(), role: "closed", content: "" }]);
          return;
        }
        if (ending) {
          setHeldProposals(finalProposals);
          finishGrow(endRecord ?? "");
        } else if (wrapUp) {
          // The coach was told to wrap up and didn't call `end_session`. Its
          // reply is all we have, so end on that rather than leaving a session
          // nothing can close — the overrun backstop has already been spent.
          setHeldProposals(finalProposals);
          finishGrow(accumulated.trim());
        }
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
        chatToast.error(msg);
        // Never strand the user inside the ending sequence.
        if (goodbye) {
          goodbyeRef.current = false;
          setGmsgs((p) => [...p, { id: uid(), role: "closed", content: "" }]);
        } else if (wrapUp) {
          finishGrow();
        }
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
        chatToast.error(
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
    "[We are well past the time set for this session, so wrap it up now. Work only " +
    "from what actually happened — if we never got to a commitment, say so rather " +
    "than writing it up as though we did. Call end_session with the record, and in " +
    "the same reply propose only what this session genuinely supports adding to the " +
    "goal (which may be nothing). No goodbye yet.]";

  /** The user pressed End and asked for a proper close rather than just quitting. */
  const EARLY_END_INSTRUCTION =
    "[I am ending this session now, before it reached its natural end. Close it " +
    "honestly: base everything only on what we actually covered, name what we did " +
    "and did not get to, and do not present it as a completed session. Call " +
    "end_session with that record, and propose something for the goal only if this " +
    "conversation really supports it — most likely nothing. No goodbye yet.]";

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

  /**
   * Step 1 of the ending: the user has decided on the session record. This no
   * longer leaves the session — the held proposals are released next, and the
   * goodbye comes after those. `leaveGrow` is what actually exits.
   */
  const closeSession = (save: boolean) => {
    let saved = false;
    if (save && context.goalId && memoryDraft?.trim()) {
      saved = true;
      saveSessionMemory(context.goalId, memoryDraft).catch(() =>
        chatToast.error(
          "Couldn't save the session memory — it won't carry over.",
        ),
      );
    }
    memorySavedRef.current = saved;
    setMemoryDraft(null);
    clearPendingEnd(context.goalId); // the user decided — the card may rest
    // A card restored after a reload has no session behind it: there is nothing
    // left to propose and nobody to say goodbye. Decide the record and stop.
    if (!inGrow) {
      setMsgs((p) => [
        ...p,
        {
          id: uid(),
          role: "system",
          content: saved
            ? "Session memory saved."
            : "Session ended without saving memory.",
        },
      ]);
      return;
    }
    // Drop the record card, then show whatever the coach proposed.
    setGmsgs((p) => p.filter((m) => m.role !== "end"));
    if (heldProposals.length) {
      setGmsgs((p) => [
        ...p,
        {
          id: uid(),
          role: "assistant",
          content: "I've prepared this for your review.",
          proposals: heldProposals,
        },
      ]);
      setHeldProposals([]);
      setMode("grow-review");
    } else {
      askForGoodbye();
    }
  };

  /**
   * Step 2 → 3: everything has been decided, so ask the coach for the goodbye.
   * It is told what the user actually kept, because a farewell that thanks
   * someone for accepting what they rejected is worse than none.
   */
  const askForGoodbye = () => {
    const kept = gmsgs.reduce(
      (n, m) =>
        n + (m.proposals?.filter((pr) => pr.status === "approved").length ?? 0),
      0,
    );
    const declined = gmsgs.reduce(
      (n, m) =>
        n + (m.proposals?.filter((pr) => pr.status === "rejected").length ?? 0),
      0,
    );
    setMode("grow-farewell");
    goodbyeRef.current = true;
    sendGrowRef.current(
      "[The user has now decided what to keep from this session. " +
        (memorySavedRef.current
          ? "They saved the session record. "
          : "They chose not to save the session record. ") +
        `They accepted ${kept} and declined ${declined} of the changes you proposed. ` +
        (endKindRef.current === "early"
          ? "Remember they ended this session early, so keep it honest. "
          : "") +
        "Say your goodbye now, in the language we have been speaking: short, " +
        "human, and shaped by what they actually kept. Do not repeat the " +
        "summary, do not propose anything, do not call any tools, and do not " +
        "ask a question.]",
      { wrapUp: true, goodbye: true },
    );
  };

  /** The session is over: leave GROW mode and note what became of it. */
  const leaveGrow = (opts?: { silent?: boolean }) => {
    setMode("chat");
    setSession(null);
    setMemoryDraft(null);
    setHeldProposals([]);
    clearPendingEnd(context.goalId);
    clearGrowSession(context.goalId);
    if (opts?.silent) {
      setMsgs((p) => [
        ...p,
        { id: uid(), role: "system", content: "Session ended." },
      ]);
      return;
    }
    const pending = sessionProposals;
    const note =
      (memorySavedRef.current
        ? "Session memory saved."
        : "Session ended without saving memory.") +
      (pending > 0
        ? ` ${pending} proposal${pending === 1 ? "" : "s"} from the session await your review.`
        : "");
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
    // Not clamped at zero: overrun is how the backstop measures itself, so a
    // session resumed long after its time is immediately past the grace period
    // instead of starting another ten-minute wait.
    const remaining = Math.round((stored.endsAt - Date.now()) / 1000);
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
        // Allowed to go negative: the coach owns the ending, so overrun is
        // normal and is what the hard stop below measures.
        return { ...s, remaining: s.remaining - 1 };
      });
    }, 1000);
    return () => clearInterval(iv);
  }, [mode, session?.total]);

  useEffect(() => {
    if (!session) return;
    const frac = 1 - session.remaining / session.total;
    if (frac >= 0.8 && mode === "grow-active") setMode("grow-closing");
    // The clock no longer ends the session — the coach does, via `end_session`.
    // This is only the backstop for a coach that never calls it: at
    // OVERRUN_GRACE past the planned end it is TOLD to wrap up, so the ending
    // is still analysed rather than fabricated by the UI.
    if (
      session.remaining <= -OVERRUN_GRACE_SECONDS &&
      !endedRef.current &&
      !wrapUpRef.current &&
      !busy &&
      (mode === "grow-active" || mode === "grow-closing")
    ) {
      wrapUpRef.current = true;
      endKindRef.current = "overrun";
      sendGrowRef.current(WRAP_UP_INSTRUCTION, { wrapUp: true });
    }
  }, [session, mode, busy, WRAP_UP_INSTRUCTION]);

  // The proposals step is over as soon as nothing is pending — then the coach
  // is asked for its goodbye. Guarded by goodbyeRef so it fires exactly once.
  useEffect(() => {
    if (mode !== "grow-review" || busy || goodbyeRef.current) return;
    if (sessionProposals > 0) return;
    askForGoodbye();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, busy, sessionProposals]);

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
      saveAiProvider(provId); // sync the choice across devices
      chatToast.success(`${provId} key saved`);
    } catch (e) {
      chatToast.error(e instanceof Error ? e.message : "Failed to save key");
    }
  };

  const handleActivateProvider = (id: string) => {
    setActiveProv(id);
    saveActiveProvider(id);
    saveAiProvider(id); // sync the choice across devices
  };

  const handleSaveTavily = async (raw: string) => {
    const hint = raw.length > 8 ? `••••${raw.slice(-4)}` : "••••";
    try {
      await saveApiKey("TAVILY", raw);
      setTavily({ connected: true, hint });
      chatToast.success("Web search connected");
    } catch (e) {
      chatToast.error(e instanceof Error ? e.message : "Failed to save key");
    }
  };

  const handleModelChange = async (provId: string, model: string) => {
    setProviders((ps) =>
      ps.map((p) => (p.id === provId ? { ...p, activeModel: model } : p)),
    );
    try {
      await updateKeyModel(provId, model);
    } catch (e) {
      chatToast.error(
        e instanceof Error ? e.message : "Failed to update model",
      );
    }
  };

  // ── Timer display ─────────────────────────────────────────────────────────

  let timerLabel = "";
  let timerFrac = 0;
  const closing = mode === "grow-closing";
  const overtime = !!session && session.remaining < 0;
  if (session) {
    const rem = Math.ceil(Math.abs(session.remaining));
    const mm = String(Math.floor(rem / 60)).padStart(2, "0");
    const ss = String(rem % 60).padStart(2, "0");
    // Past the planned end the clock counts up, prefixed — the session is not
    // over until the coach ends it, so a frozen 00:00 would be a lie.
    timerLabel = `${overtime ? "+" : ""}${mm}:${ss}`;
    timerFrac = Math.max(0, session.remaining / session.total);
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
          ).catch((err) => {
            // The user explicitly approved or rejected this card. If the server never
            // hears about it the row stays pending and the proposal comes back later,
            // which reads as the app forgetting a decision the user made.
            logger.reportError(err, { kind: "api" });
          });
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
      {/* Chrome band — the wordmark row and the provider strip together. It carries **Kale-500**
          (the header stays dark teal) while the conversation below sits on a light teal gradient,
          so the header reads as chrome over the chat. */}
      <div className="shrink-0 bg-[#0A8080]">
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
                  onClick={() => setConfirmEnd(true)}
                  disabled={!canEndEarly}
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full border border-white/30 bg-transparent text-white text-xs font-semibold hover:bg-white/10 disabled:opacity-40 disabled:hover:bg-transparent transition-colors"
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
                    <Ic path={PATHS.circlePlus} size={14} /> New chat
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
                  // The chat gradient's own two colours (owner, 2026-08-17): connected takes the
                  // teal top-of-gradient step, a missing key the pale bottom step — the state still
                  // reads and the dot ties back to the conversation's palette.
                  activeProvider.connected
                    ? "bg-[#83D2D2] shadow-[0_0_0_3px_rgba(131,210,210,0.25)]"
                    : "bg-[#F2FFFF] shadow-[0_0_0_3px_rgba(242,255,255,0.3)]",
                )}
              />
              {activeLabel}
            </span>
          </div>
        )}
      </div>

      {/* Closing banner */}
      {closing && (
        <div className="mx-4 mb-2.5 px-3 py-2.5 rounded-[10px] bg-white/10 flex items-center gap-2 text-[12.5px] text-white">
          <SproutArt size={14} />
          The session is gently moving toward a close
        </div>
      )}

      {/* Body **and footer on one surface** — the light teal gradient (owner's design, 2026-08-15).
          It used to be painted on the scroll area alone, with the composer's own `#F2FFFF` block
          butted underneath it; that block was a second visible container, so the white input card
          read as a box inside a box (owner, 2026-08-17). One gradient, everything on it
          transparent, and the only thing you see is the field — which is what Android does. */}
      <div
        style={{
          backgroundImage:
            "linear-gradient(180deg, #83D2D2 3.43%, #F2FFFF 118.85%)",
        }}
        className="flex min-h-0 flex-1 flex-col"
      >
        <div
          ref={scrollRef}
          data-vaul-no-drag
          className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden px-4 py-2 flex flex-col gap-4 scrollbar-thin scrollbar-thumb-black/15"
        >
          {/* Empty state */}
          {!inGrow && msgs.length === 0 && (
            <div className="pt-5 pb-2 text-center">
              {/* **The watering can and sprout** — the owner's illustration, the same artwork
                  Android draws (`SpiraArt.sprout`), and it is vector rather than a bitmap. It
                  replaced a plain leaf glyph here on 2026-08-17, so the two surfaces head the
                  assistant with one picture instead of two ideas of it. */}
              <div className="w-[52px] h-[52px] rounded-full bg-white/70 border border-[#005961]/20 grid place-items-center mx-auto mb-4">
                <SproutArt size={26} />
              </div>
              <p className="text-[14px] leading-[1.6] text-[#003737] max-w-[30ch] mx-auto mb-5">
                {goal
                  ? `I'm here to help with "${goal.title}". Ask anything or start a GROW session.`
                  : "I'm here to help you think. Ask me anything, or just say what you want to achieve and I'll help you create a new goal."}
              </p>
              {/* **No starters inside a goal** (owner, 2026-08-17). On the dashboard they answer
                "what is this for?"; inside a goal the user already knows why they opened the
                assistant, and a stack of guesses about their own goal was in the way of typing. */}
              <div className="flex flex-col gap-2">
                {(goal ? [] : SUGGESTIONS_GLOBAL).map((s) => (
                  <button
                    key={s.id}
                    onClick={() => sendChat(s.text)}
                    className="flex items-center gap-2.5 px-3.5 py-3 rounded-xl bg-white/80 border border-[#005961]/15 text-[#003737] text-[13.5px] text-left shadow-sm hover:border-[#005961]/35 hover:-translate-y-px transition-all"
                  >
                    <Ic
                      path={PATHS[s.icon as IconKey] ?? PATHS.sparkles}
                      size={15}
                      className="shrink-0 text-[#0A8080]"
                    />
                    <span className="flex-1">{s.text}</span>
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
                  {/* A change typed on a proposal card — say which card it belongs to, so the
                    request reads as part of the conversation and not as a stray message. */}
                  {m.revisedLabel && (
                    <span className="flex max-w-[86%] items-center gap-1.5 pr-1 text-[12px] text-[#005961]/80">
                      <Ic path={PATHS.pencil} size={12} className="shrink-0" />
                      <span className="truncate">
                        Change to «{m.revisedLabel}»
                      </span>
                    </span>
                  )}
                  {m.attachments && m.attachments.length > 0 && (
                    <div className="flex max-w-[86%] flex-wrap justify-end gap-1.5">
                      {m.attachments.map((a, i) => {
                        const chipClass =
                          "inline-flex max-w-[220px] items-center gap-1.5 rounded-lg bg-white/85 px-2 py-1 text-[12px] text-[#003737] shadow-sm";
                        // What a chip opens is one rule for both chip sites — see
                        // `attachmentOpener`. An image's dataUrl survives only in-session
                        // (it is stripped before the transcript is persisted), so a reloaded
                        // chat falls back to a plain chip; a resource stays openable, because
                        // it is resolved from the goal rather than from the message.
                        const open = attachmentOpener(
                          a,
                          goal?.resources,
                          setContentModal,
                        );
                        return open ? (
                          <button
                            key={i}
                            type="button"
                            onClick={open}
                            aria-label={`Open ${a.name}`}
                            className={`${chipClass} cursor-pointer transition-colors hover:bg-white`}
                          >
                            <Paperclip className="h-3 w-3 shrink-0 opacity-60" />
                            <span className="truncate">{a.name}</span>
                          </button>
                        ) : (
                          <span key={i} className={chipClass}>
                            <Paperclip className="h-3 w-3 shrink-0 opacity-60" />
                            <span className="truncate">{a.name}</span>
                          </span>
                        );
                      })}
                    </div>
                  )}
                  <div className="max-w-[86%] min-w-0 px-3.5 py-2.5 rounded-2xl rounded-br-sm bg-white text-[#003737] text-[14px] leading-[1.5] whitespace-pre-wrap break-words [overflow-wrap:anywhere] select-text selection:bg-[#005961]/25 selection:text-[#003737]">
                    {m.content}
                  </div>
                  <CopyButton text={m.content} tone="dark" />
                </div>
              );
            }
            if (m.role === "system") {
              return (
                <div
                  key={m.id}
                  className="flex items-center gap-2 self-center text-[12.5px] text-[#003737] bg-white/70 px-3 py-1.5 rounded-full"
                >
                  <Ic path={PATHS.check} size={12} /> {m.content}
                </div>
              );
            }
            if (m.role === "closed") {
              return (
                <div
                  key={m.id}
                  className="rounded-[14px] border border-white/20 bg-white text-[#003737] p-4 shadow-[0_6px_20px_-14px_rgba(0,0,0,0.4)]"
                >
                  <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#005961]">
                    <Ic path={PATHS.check} size={12} /> Session complete
                  </span>
                  <button
                    onClick={() => leaveGrow()}
                    className="mt-3 w-full h-10 rounded-[9px] bg-[#0A8080] text-white text-[13.5px] font-semibold hover:bg-[#005961] transition-colors"
                  >
                    Close
                  </button>
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
                  className="flex items-start gap-2 text-[14px] leading-[1.55] text-[#C99500] max-w-[94%] min-w-0 break-words [overflow-wrap:anywhere]"
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
              <div
                key={m.id}
                className="group flex flex-col items-start gap-2.5"
              >
                {/* **A bubble, like the user's, in a different colour** (owner, 2026-08-17). The
                  assistant's prose used to sit straight on the gradient, which read as text
                  floating loose in the page rather than as a message from someone. Same capsule as
                  the user's, mirrored (the squared corner is bottom-LEFT, so each turn leans to its
                  own side) and filled **Kale-200 #E0F2F5** — not white, which is the user's, and
                  not the gradient's own #F2FFFF, which at the foot of the chat IS the ground. */}
                <div className="max-w-[94%] min-w-0 rounded-2xl rounded-bl-sm bg-[#E0F2F5] px-3.5 py-2.5 text-[14.5px] leading-[1.62] text-[#182928] break-words [overflow-wrap:anywhere] select-text selection:bg-[#005961]/25 selection:text-[#003737]">
                  {m.streaming && !m.content ? (
                    // Waiting for the answer — the three-dot loader in the chat's own teal.
                    <ThinkingDots />
                  ) : (
                    <>
                      <Markdown text={m.content} />
                      {m.streaming && (
                        <span className="inline-block w-[7px] h-[15px] ml-0.5 align-text-bottom bg-[#0A8080] rounded-sm animate-pulse" />
                      )}
                    </>
                  )}
                </div>
                {!m.streaming && m.content && (
                  <CopyButton text={m.content} tone="dark" />
                )}
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

        {/* The chat's toast rides directly above whatever the footer is showing — the
          composer, or the card that has taken its place — at exactly that thing's width.
          `px-3 sm:px-4` is the composer's own gutter, so the two line up on any screen. */}
        {notice && (
          <div className="shrink-0 px-3 pt-1 sm:px-4">
            <NoticeCard
              kind={notice.kind}
              onDismiss={() => setNotice(null)}
              role={notice.kind === "error" ? "alert" : "status"}
            >
              {notice.message}
            </NoticeCard>
          </div>
        )}

        {/* Footer. While revising a card, show a cancellable "Revising…" state; otherwise a
          pending card renders here (the card IS the input); otherwise the composer. */}
        {mode === "grow-review" && !busy && sessionProposals > 0 && (
          <div className="bg-[#F2FFFF] px-3 pb-3 pt-1 shrink-0">
            <button
              onClick={() => !goodbyeRef.current && askForGoodbye()}
              className="w-full h-11 rounded-[14px] border border-[#005961]/30 bg-white text-[#005961] text-[13.5px] font-semibold hover:bg-[#005961]/5 transition-colors"
            >
              Finish session
            </button>
            <p className="mt-1.5 text-center text-[11.5px] text-[#003737]/45">
              Anything you leave undecided stays waiting in the goal.
            </p>
          </div>
        )}

        {mode !== "grow-end" && revising && (
          <div className="bg-[#F2FFFF] px-3 pb-3 pt-1 shrink-0">
            <div className="flex items-center gap-2.5 rounded-[14px] border border-black/10 bg-white px-4 py-3 text-[#003737] shadow-[0_6px_20px_-14px_rgba(0,0,0,0.4)]">
              <span className="h-4 w-4 shrink-0 rounded-full border-2 border-[#005961]/30 border-t-[#005961] animate-spin" />
              <span className="flex-1 min-w-0 text-[13.5px] truncate">
                Revising «{revising.label}»…
              </span>
              <button
                onClick={cancelRevise}
                className="shrink-0 text-[13px] font-medium text-[#003737]/55 hover:text-red-600 transition-colors px-1.5 py-1"
              >
                Cancel
              </button>
            </div>
          </div>
        )}
        {/* Note: shown in grow-end too — the wrap-up turn may propose capturing
          the user's commitments, and those cards must stay actionable. */}
        {!revising && pendingMsg && (
          // `flex-initial` — that is `flex: 0 1 auto`, and each of the three numbers is doing
          // a job:
          //
          //   grow 0    take no space the card does not need. `flex-1` was tried here and left
          //             a band of empty gradient under a short card, because grow 1 claims a
          //             share of the panel whether or not there is anything to put in it
          //             (owner, 2026-08-28, with a screenshot of exactly that).
          //   shrink 1  give way when the drawer is short. The original `shrink-0` could not,
          //             so on a squeezed viewport the card was pushed past the bottom edge and
          //             Accept could not be reached at all.
          //   basis auto  size to the card.
          //
          // The cap then stops a very tall proposal (a stepper, a long preview) from squeezing
          // the transcript to nothing, and the card scrolls inside it — with its action row
          // `sticky bottom-0`, so Accept stays reachable however far it scrolls.
          <div className="min-h-0 flex-initial max-h-[70%] px-3 pb-3 pt-1 overflow-y-auto overflow-x-hidden scrollbar-thin scrollbar-thumb-black/15">
            {proposalGroupFor(pendingMsg)}
          </div>
        )}
        {mode !== "grow-end" && !revising && !pendingMsg && (
          <>
            <Composer
              onSend={(text, attachments) =>
                inGrow ? sendGrow(text) : sendChat(text, attachments)
              }
              allowAttachments={!inGrow}
              // A GROW session is ephemeral by design, so nothing typed into one is kept.
              draftScope={inGrow ? undefined : scopeKey}
              attachResources={
                !inGrow && goal
                  ? goal.resources.map((r) => ({
                      id: r.id,
                      label: r.type === "email" ? r.name : r.title,
                      // The raw type, so the picker can draw the same glyph Android's does.
                      type: r.type,
                      typeLabel: resourceTypeMeta[r.type].label,
                      mime: r.type === "file" ? r.mime : undefined,
                    }))
                  : undefined
              }
              resolveAttachmentOpen={(a) =>
                attachmentOpener(a, goal?.resources, setContentModal)
              }
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
                inGrow ? (
                  // The early-stop lives where "Start GROW session" was — same slot, so ending a
                  // session is where starting one is (owner, 2026-08-17).
                  <button
                    onClick={() => setConfirmEnd(true)}
                    disabled={!canEndEarly}
                    className="inline-flex items-center gap-1.5 px-2 h-8 shrink-0 rounded-lg text-[#005961] text-[13px] font-medium hover:bg-[#005961]/10 disabled:opacity-40 disabled:hover:bg-transparent transition-colors"
                  >
                    <Ic path={PATHS.x} size={14} /> End session early
                  </button>
                ) : goal ? (
                  <button
                    onClick={() => setMode("grow-start")}
                    className="inline-flex items-center gap-1.5 px-2 h-8 shrink-0 rounded-lg text-[#0A8080] text-[13px] font-medium hover:bg-[#0A8080]/10 transition-colors"
                  >
                    <Ic path={PATHS.growSparkles} size={15} /> Start GROW
                    session
                  </button>
                ) : undefined
              }
            />
          </>
        )}
      </div>

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
          remainingLabel={overtime ? "" : timerLabel}
          onConfirm={() => {
            setConfirmEnd(false);
            if (busy) return;
            endKindRef.current = "early";
            wrapUpRef.current = true;
            sendGrowRef.current(EARLY_END_INSTRUCTION, { wrapUp: true });
          }}
          onQuit={() => {
            setConfirmEnd(false);
            // Abandon any turn in flight, or it keeps streaming (and possibly
            // persisting proposals) into a transcript nobody will see again.
            stopRef.current = true;
            setBusy(false);
            endedRef.current = true;
            leaveGrow({ silent: true });
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
                  chatToast.success("Goal deleted");
                } else if (pendingDelete.goalId) {
                  removeTarget(pendingDelete.goalId, pendingDelete.id);
                  chatToast.success("Target deleted");
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
          image={contentModal.image}
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
  image,
  onClose,
}: {
  title: string;
  body: string;
  html?: boolean;
  image?: string;
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
      className="absolute inset-0 z-50 flex items-center justify-center p-4 bg-[rgba(0,55,55,0.45)] backdrop-blur-[2px]"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-[440px] max-h-[80%] flex flex-col bg-white text-[#003737] rounded-[18px] shadow-[0_20px_60px_-20px_rgba(0,0,0,0.55)]"
        style={{ animation: "slideUp 0.25s cubic-bezier(0.2,0.8,0.2,1) both" }}
      >
        <div className="flex items-start gap-3 px-5 pt-4 pb-3 border-b border-[#F3F3F3]">
          <h3 className="font-['Playfair_Display'] text-[18px] font-semibold leading-[1.25] flex-1 break-words [overflow-wrap:anywhere]">
            {title}
          </h3>
          <button
            onClick={onClose}
            aria-label="Close"
            className="shrink-0 text-[#003737]/40 hover:text-[#003737] transition-colors p-1 -mr-1"
          >
            <X size={18} />
          </button>
        </div>
        <div className="px-5 py-4 overflow-y-auto text-[14px] leading-[1.6] text-[#003737]/85 [overflow-wrap:anywhere]">
          {/* An attached image previews as the image itself; notes are stored as HTML
              (TipTap) → render formatted like the note view; goal descriptions are
              plain text → Markdown. */}
          {image ? (
            <img
              src={image}
              alt={title}
              className="mx-auto max-h-full max-w-full rounded-lg object-contain"
            />
          ) : html ? (
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
  // Gravity `folder-open` — the attach menu's "From resources" mark, the same glyph the Android
  // menu carries (`SpiraIcons.FolderOpen`), so the same row reads the same on both surfaces.
  folderOpen:
    "<path fill='currentColor' fill-rule='evenodd' d='m6.379 4.5l-.44-.44l-.621-.62A1.5 1.5 0 0 0 4.258 3H3a1.5 1.5 0 0 0-1.5 1.5v5.25l1.376-2.293A3 3 0 0 1 5.45 6h7.05A1.5 1.5 0 0 0 11 4.5zM14 6.026V6a3 3 0 0 0-3-3H7l-.621-.621A3 3 0 0 0 4.257 1.5H3a3 3 0 0 0-3 3V11a3 3 0 0 0 3 3h8.301a3 3 0 0 0 2.573-1.457l1.791-2.985A2.35 2.35 0 0 0 14 6.026M10 12.5h1.301a1.5 1.5 0 0 0 1.287-.728l1.791-2.986l1.286.772l-1.286-.772a.85.85 0 0 0-.728-1.286H5.449a1.5 1.5 0 0 0-1.287.728l-1.791 2.986a.85.85 0 0 0 .728 1.286z'/>",
  leaf: "<path fill='currentColor' fill-rule='evenodd' d='M6.943 8.703L4.301 6.3a.25.25 0 0 0-.355.02L2.299 8.171a.25.25 0 0 0 .023.355l4.785 4.147a.25.25 0 0 0 .36-.032L13.29 5.36a.25.25 0 0 0-.03-.343l-1.856-1.65a.25.25 0 0 0-.364.034zM6.75 6.5l3.104-4.017a1.75 1.75 0 0 1 2.547-.238l1.857 1.651a1.75 1.75 0 0 1 .204 2.401L8.637 13.58a1.75 1.75 0 0 1-2.512.229L1.339 9.66a1.75 1.75 0 0 1-.162-2.486l1.647-1.852A1.75 1.75 0 0 1 5.31 5.19z'/>",
  key: "<path fill='currentColor' fill-rule='evenodd' d='M10.313 7.488L9 7.653v5.37a.5.5 0 0 1-.353.478l-1.62.498l-.006.001h-.008l-.007-.006l-.005-.007v-.003L7 13.979V7.653l-1.313-.165a1.5 1.5 0 0 1-1.271-1.144l-.588-2.5A1.5 1.5 0 0 1 5.288 2h5.424a1.5 1.5 0 0 1 1.46 1.844l-.588 2.5a1.5 1.5 0 0 1-1.271 1.144m2.731-.8A3 3 0 0 1 10.5 8.976v4.046a2 2 0 0 1-1.412 1.911l-1.62.499A1.52 1.52 0 0 1 5.5 13.979V8.977a3 3 0 0 1-2.544-2.29l-.588-2.5A3 3 0 0 1 5.288.5h5.424a3 3 0 0 1 2.92 3.687zM6.75 3.5a.75.75 0 0 0 0 1.5h2.5a.75.75 0 0 0 0-1.5z'/>",
  chevron:
    "<path fill='currentColor' fill-rule='evenodd' d='M2.97 5.47a.75.75 0 0 1 1.06 0L8 9.44l3.97-3.97a.75.75 0 1 1 1.06 1.06l-4.5 4.5a.75.75 0 0 1-1.06 0l-4.5-4.5a.75.75 0 0 1 0-1.06'/>",
  clock:
    "<path fill='currentColor' fill-rule='evenodd' d='M13.5 8a5.5 5.5 0 1 1-11 0a5.5 5.5 0 0 1 11 0M15 8A7 7 0 1 1 1 8a7 7 0 0 1 14 0M8.75 4.5a.75.75 0 0 0-1.5 0V8a.75.75 0 0 0 .3.6l2 1.5a.75.75 0 1 0 .9-1.2l-1.7-1.275z'/>",
  check:
    "<path fill='currentColor' fill-rule='evenodd' d='M13.488 3.43a.75.75 0 0 1 .081 1.058l-6 7a.75.75 0 0 1-1.1.042l-3.5-3.5A.75.75 0 0 1 4.03 6.97l2.928 2.927l5.473-6.385a.75.75 0 0 1 1.057-.081'/>",
  x: "<path fill='currentColor' fill-rule='evenodd' d='M3.47 3.47a.75.75 0 0 1 1.06 0L8 6.94l3.47-3.47a.75.75 0 1 1 1.06 1.06L9.06 8l3.47 3.47a.75.75 0 1 1-1.06 1.06L8 9.06l-3.47 3.47a.75.75 0 0 1-1.06-1.06L6.94 8L3.47 4.53a.75.75 0 0 1 0-1.06'/>",
  sparkles:
    "<path fill='currentColor' fill-rule='evenodd' d='M13 10a.75.75 0 0 1 .725.556a2.37 2.37 0 0 0 1.72 1.72a.75.75 0 0 1 0 1.449a2.37 2.37 0 0 0-1.72 1.72a.75.75 0 0 1-1.45 0a2.37 2.37 0 0 0-1.72-1.72a.75.75 0 0 1 0-1.45a2.37 2.37 0 0 0 1.72-1.72l.043-.117A.75.75 0 0 1 13 10M7 0a1.5 1.5 0 0 1 1.48 1.253c.242 1.455.696 2.364 1.3 2.968c.603.603 1.512 1.057 2.967 1.3a1.5 1.5 0 0 1 0 2.958c-1.455.243-2.364.697-2.968 1.3c-.603.604-1.057 1.513-1.3 2.968a1.5 1.5 0 0 1-2.958 0c-.243-1.455-.697-2.364-1.3-2.968c-.604-.603-1.513-1.057-2.968-1.3a1.5 1.5 0 0 1 0-2.958c1.455-.243 2.364-.697 2.968-1.3c.603-.604 1.057-1.513 1.3-2.968l.028-.133A1.5 1.5 0 0 1 7 0m0 1.5C6.45 4.8 4.8 6.45 1.5 7c3.3.55 4.95 2.2 5.5 5.5c.55-3.3 2.2-4.95 5.5-5.5C9.2 6.45 7.55 4.8 7 1.5'/>",
  growSparkles:
    "<path fill='currentColor' fill-rule='evenodd' d='M13 10a.75.75 0 0 1 .725.556a2.37 2.37 0 0 0 1.72 1.72a.75.75 0 0 1 0 1.449a2.37 2.37 0 0 0-1.72 1.72a.75.75 0 0 1-1.45 0a2.37 2.37 0 0 0-1.72-1.72a.75.75 0 0 1 0-1.45a2.37 2.37 0 0 0 1.72-1.72l.043-.117A.75.75 0 0 1 13 10M7 0a1 1 0 0 1 .986.836c.279 1.67.815 2.8 1.596 3.582c.781.781 1.912 1.317 3.582 1.596a1 1 0 0 1 0 1.972c-1.67.279-2.8.815-3.582 1.596c-.781.781-1.317 1.912-1.596 3.582a1 1 0 0 1-1.972 0c-.279-1.67-.815-2.8-1.596-3.582c-.781-.781-1.912-1.317-3.582-1.596a1 1 0 0 1 0-1.972c1.67-.279 2.8-.815 3.582-1.596c.781-.781 1.317-1.912 1.596-3.582l.018-.089A1 1 0 0 1 7 0'/>",
  brain:
    "<path fill='currentColor' fill-rule='evenodd' d='M6.26 15.109a4 4 0 0 0 3.48 0l.13-.063a2 2 0 0 0 1.13-1.8v-.468c0-1.352.776-2.557 1.54-3.673a5.5 5.5 0 1 0-9.08 0C4.224 10.221 5 11.426 5 12.779v.467a2 2 0 0 0 1.13 1.801zm2.828-1.35l.13-.064a.5.5 0 0 0 .282-.45v-.467q0-.255.025-.5a5.33 5.33 0 0 1-3.05 0q.024.245.025.5v.467a.5.5 0 0 0 .282.45l.13.063a2.5 2.5 0 0 0 2.176 0m-4.39-5.501c.394.576.891 1.302 1.263 2.148a3.79 3.79 0 0 0 4.078 0c.372-.846.869-1.572 1.264-2.148a4 4 0 1 0-6.605 0'/><path fill='currentColor' fill-rule='evenodd' d='M8 3.5A.75.75 0 0 0 8 5a1 1 0 0 1 1 1a.75.75 0 0 0 1.5 0A2.5 2.5 0 0 0 8 3.5'/>",
  shield:
    "<path fill='currentColor' fill-rule='evenodd' d='m3.003 4.702l4.22-2.025a1.8 1.8 0 0 1 1.554 0l4.22 2.025a.89.89 0 0 1 .503.8V6a8.55 8.55 0 0 1-3.941 7.201l-.986.631a1.06 1.06 0 0 1-1.146 0l-.986-.63A8.55 8.55 0 0 1 2.5 6v-.498c0-.341.196-.652.503-.8m3.57-3.377L2.354 3.35A2.39 2.39 0 0 0 1 5.502V6a10.05 10.05 0 0 0 4.632 8.465l.986.63a2.56 2.56 0 0 0 2.764 0l.986-.63A10.05 10.05 0 0 0 15 6v-.498c0-.918-.526-1.755-1.354-2.152l-4.22-2.025a3.3 3.3 0 0 0-2.852 0M8.47 9.97a.75.75 0 1 0 1.06 1.06c.575-.574 1.118-1.398 1.516-2.195c.386-.772.704-1.653.704-2.335a.75.75 0 0 0-1.5 0c0 .318-.182.937-.546 1.665c-.352.703-.809 1.379-1.234 1.805'/>",
  plus: "<path fill='currentColor' fill-rule='evenodd' d='M8 1.75a.75.75 0 0 1 .75.75v4.75h4.75a.75.75 0 0 1 0 1.5H8.75v4.75a.75.75 0 0 1-1.5 0V8.75H2.5a.75.75 0 0 1 0-1.5h4.75V2.5A.75.75 0 0 1 8 1.75'/>",
  circlePlus:
    "<path fill='currentColor' fill-rule='evenodd' d='M13.5 8a5.5 5.5 0 1 1-11 0a5.5 5.5 0 0 1 11 0M15 8A7 7 0 1 1 1 8a7 7 0 0 1 14 0M8.75 5.5a.75.75 0 0 0-1.5 0v1.75H5.5a.75.75 0 1 0 0 1.5h1.75v1.75a.75.75 0 0 0 1.5 0V8.75h1.75a.75.75 0 0 0 0-1.5H8.75z'/>",
  switch_:
    "<path fill='currentColor' fill-rule='evenodd' d='M13.78 3.72a.75.75 0 0 1 0 1.06l-3 3a.75.75 0 1 1-1.06-1.06L11.44 5H2.75a.75.75 0 1 1 0-1.5h8.69L9.72 1.78A.75.75 0 0 1 10.78.72zM2 11.75a.75.75 0 0 1 .22-.53l3-3a.75.75 0 1 1 1.06 1.06L4.56 11h8.69a.75.75 0 0 1 0 1.5H4.56l1.72 1.72a.75.75 0 1 1-1.06 1.06l-3-3a.75.75 0 0 1-.22-.53'/>",
  pencil:
    "<path fill='currentColor' fill-rule='evenodd' d='M11.423 1A3.577 3.577 0 0 1 15 4.577c0 .27-.108.53-.3.722l-.528.529l-1.971 1.971l-5.059 5.059a3 3 0 0 1-1.533.82l-2.638.528a1 1 0 0 1-1.177-1.177l.528-2.638a3 3 0 0 1 .82-1.533l5.059-5.059l2.5-2.5c.191-.191.451-.299.722-.299m-2.31 4.009l-4.91 4.91a1.5 1.5 0 0 0-.41.766l-.38 1.903l1.902-.38a1.5 1.5 0 0 0 .767-.41l4.91-4.91a2.08 2.08 0 0 0-1.88-1.88m3.098.658a3.6 3.6 0 0 0-1.878-1.879l1.28-1.28c.995.09 1.788.884 1.878 1.88z'/>",
  target:
    "<path fill='currentColor' fill-rule='evenodd' d='M8 13.5a5.5 5.5 0 1 0 0-11a5.5 5.5 0 0 0 0 11M8 15A7 7 0 1 0 8 1a7 7 0 0 0 0 14m0-4.5a2.5 2.5 0 1 0 0-5a2.5 2.5 0 0 0 0 5M8 12a4 4 0 1 0 0-8a4 4 0 0 0 0 8m0-3a1 1 0 1 0 0-2a1 1 0 0 0 0 2'/>",
  copy: "<path fill='currentColor' fill-rule='evenodd' d='M12 2.5H8A1.5 1.5 0 0 0 6.5 4v1H8a3 3 0 0 1 3 3v1.5h1A1.5 1.5 0 0 0 13.5 8V4A1.5 1.5 0 0 0 12 2.5M11 11h1a3 3 0 0 0 3-3V4a3 3 0 0 0-3-3H8a3 3 0 0 0-3 3v1H4a3 3 0 0 0-3 3v4a3 3 0 0 0 3 3h4a3 3 0 0 0 3-3zM4 6.5h4A1.5 1.5 0 0 1 9.5 8v4A1.5 1.5 0 0 1 8 13.5H4A1.5 1.5 0 0 1 2.5 12V8A1.5 1.5 0 0 1 4 6.5'/>",
  expand:
    "<path fill='currentColor' fill-rule='evenodd' d='M7.754 2.004a.75.75 0 0 0 0 1.5h4.75v4.742a.75.75 0 0 0 1.5 0V2.754a.75.75 0 0 0-.75-.75zm.492 11.992a.75.75 0 0 0 0-1.5h-4.75V7.754a.75.75 0 0 0-1.5 0v5.492a.75.75 0 0 0 .75.75z'/>",
  zap: "<path fill='currentColor' fill-rule='evenodd' d='M9.262.498a.75.75 0 0 1 1.275.717L9.229 5.622h3.272c1.104 0 1.665 1.328.897 2.12l-7.542 7.779a.75.75 0 0 1-1.248-.764l1.602-4.723H3.445c-1.083-.001-1.653-1.286-.926-2.09zM4.01 8.534h3.246a.75.75 0 0 1 .711.99l-.869 2.56l4.813-4.962H8.224a.75.75 0 0 1-.719-.963l.656-2.21z'/>",
  trending:
    "<path fill='currentColor' fill-rule='evenodd' d='M14.75 12.5a.75.75 0 0 1 0 1.5H1.25a.75.75 0 0 1 0-1.5zm-.5-10a.75.75 0 0 1 .75.75V6.5a.75.75 0 0 1-1.5 0V5.06l-3.241 3.242a2.25 2.25 0 0 1-2.505.465L5.335 7.69a.75.75 0 0 0-.923.261l-2.044 2.973a.75.75 0 0 1-1.236-.85l2.044-2.972a2.25 2.25 0 0 1 2.767-.782l2.42 1.075a.75.75 0 0 0 .835-.155L12.44 4H11a.75.75 0 0 1 0-1.5z'/>",
  compass:
    "<path fill='currentColor' fill-rule='evenodd' d='M13.5 8a5.5 5.5 0 1 1-11 0a5.5 5.5 0 0 1 11 0M15 8A7 7 0 1 1 1 8a7 7 0 0 1 14 0m-6.09 2.303l-2.899.805a.909.909 0 0 1-1.12-1.119l.806-2.9A2 2 0 0 1 7.09 5.697l2.9-.805a.909.909 0 0 1 1.12 1.119l-.806 2.9a2 2 0 0 1-1.392 1.392M9 8a1 1 0 1 1-2 0a1 1 0 0 1 2 0'/>",
  alert:
    "<path fill='currentColor' fill-rule='evenodd' d='M7.134 2.994L2.217 11.5a1 1 0 0 0 .866 1.5h9.834a1 1 0 0 0 .866-1.5L8.866 2.993a1 1 0 0 0-1.732 0m3.03-.75c-.962-1.665-3.366-1.665-4.329 0L.918 10.749c-.963 1.666.24 3.751 2.165 3.751h9.834c1.925 0 3.128-2.085 2.164-3.751zM8 5a.75.75 0 0 1 .75.75v2a.75.75 0 0 1-1.5 0v-2A.75.75 0 0 1 8 5m1 5.75a1 1 0 1 1-2 0a1 1 0 0 1 2 0'/>",
  trophy:
    "<path fill='currentColor' fill-rule='evenodd' d='M11.5 3.5h2a.5.5 0 0 1 .5.5v8a.5.5 0 0 1-.5.5h-2a.5.5 0 0 1-.5-.5V4a.5.5 0 0 1 .5-.5m-2.5 9a.5.5 0 0 0 .5-.5V7a.5.5 0 0 0-.5-.5H7a.5.5 0 0 0-.5.5v5a.5.5 0 0 0 .5.5zm-4.5 0A.5.5 0 0 0 5 12v-2a.5.5 0 0 0-.5-.5h-2a.5.5 0 0 0-.5.5v2a.5.5 0 0 0 .5.5zm-1 1.5h-1a2 2 0 0 1-2-2v-2a2 2 0 0 1 2-2h2q.26 0 .5.063V7a2 2 0 0 1 2-2h2q.26 0 .5.063V4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2z'/>",
  trash:
    "<path fill='currentColor' fill-rule='evenodd' d='M9 2H7a.5.5 0 0 0-.5.5V3h3v-.5A.5.5 0 0 0 9 2m2 1v-.5a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2V3H2.251a.75.75 0 0 0 0 1.5h.312l.317 7.625A3 3 0 0 0 5.878 15h4.245a3 3 0 0 0 2.997-2.875l.318-7.625h.312a.75.75 0 0 0 0-1.5zm.936 1.5H4.064l.315 7.562A1.5 1.5 0 0 0 5.878 13.5h4.245a1.5 1.5 0 0 0 1.498-1.438zm-6.186 2v5a.75.75 0 0 0 1.5 0v-5a.75.75 0 0 0-1.5 0m3.75-.75a.75.75 0 0 1 .75.75v5a.75.75 0 0 1-1.5 0v-5a.75.75 0 0 1 .75-.75'/>",
};

/** Maps a suggestion's icon key to a Gravity UI glyph (the app's set — never emoji). Falls back
 *  to the sparkles icon if a key is ever unmapped. */
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
      viewBox="0 0 16 16"
      fill="currentColor"
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
        closing && "text-[#FFDEA1]",
      )}
    >
      <Ic path={PATHS.clock} size={12} />
      <span
        className={cn(
          "text-[12.5px] font-semibold tabular-nums tracking-[0.02em]",
          closing && "text-[#FFDEA1]",
        )}
      >
        {label}
      </span>
      <span className="w-[46px] h-1 rounded-full bg-white/26 overflow-hidden">
        <span
          className="block h-full rounded-full transition-[width] duration-[900ms] linear"
          style={{
            width: `${frac * 100}%`,
            background: closing ? "#FFDEA1" : "white",
          }}
        />
      </span>
    </span>
  );
}

// ── Status badge ───────────────────────────────────────────────────────────

/**
 * The app's one badge shape: a **pill** with a bright 1px outline and a very pale fill tinted to
 * match, carrying a word and no icon. Each tone is a pair from one ramp — the 100 step fills, the
 * solid step outlines and inks — so the outline is what carries the colour and a row of badges
 * reads as one family. Mirrors the Android `SpiraBadge`.
 */
// A pill with a bright 1px outline over a nearly-white fill from the same ramp. The WORD stays
// near-black (Salt-1000) in every tone — the outline carries the meaning, and colouring the type
// too only cost legibility. Labels are sentence case, never capitals; "GROW" is the one acronym.
const BADGE_BASE =
  "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[12px] font-semibold text-[#222525]";

const BADGE_TONES = {
  // brand-800 on brand-100
  teal: "border-[#0A8080] bg-[#F9FDFC]",
  // intelligence-900 on intelligence-100 — anything belonging to the assistant
  intelligence: "border-[#6E56CF] bg-[#FEFBFF]",
  // info-900 on info-100 — a kind, a category, a neutral fact worth naming
  info: "border-[#006CC1] bg-[#FDFCFF]",
  success: "border-[#007A4B] bg-[#F8FDF7]",
  warning: "border-[#896500] bg-[#FFFBF7]",
  error: "border-[#C53336] bg-[#FFFBFB]",
  neutral: "border-[#6B6B6B] bg-[#FAFAFA]",
} as const;

function Badge({
  children,
  tone = "teal",
  className,
}: {
  children: React.ReactNode;
  tone?: keyof typeof BADGE_TONES;
  className?: string;
}) {
  return (
    <span className={cn(BADGE_BASE, BADGE_TONES[tone], className)}>
      {children}
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
  "w-full border border-[#DCDCDC] rounded-lg px-3 py-2 text-[14px] text-[#003737] bg-white outline-none focus:border-[#005961] focus:ring-2 focus:ring-[#005961]/12 transition";

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
  let body =
    p.kind === "note" || p.kind === "edit_note" || p.kind === "new_goal"
      ? p.body
      : undefined;

  const targetOf = (id?: string) => goal?.targets.find((t) => t.id === id);

  switch (p.kind) {
    // A goal edit (new title / new description) — see `editDisplay`: a long description
    // becomes a one-line preview plus "Read full content" instead of a card-stretching
    // headline.
    case "edit": {
      ({ headline, detail, body } = editDisplay(p));
      break;
    }
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
        <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#005961]">
          <Ic path={meta.icon} size={12} className="text-[#005961]" />
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
        <p className="mt-1.5 text-[13.5px] leading-[1.5] text-[#003737]/60 break-words [overflow-wrap:anywhere]">
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
          className="inline-flex items-center gap-1.5 mt-2.5 text-[12.5px] font-medium text-[#005961] hover:text-[#003737] transition-colors"
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
      <p className="text-[12px] text-[#003737]/55">
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
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#005961] text-white text-[13px] font-semibold disabled:opacity-40 hover:bg-[#003737] transition-colors"
        >
          <Ic path={PATHS.sparkles} size={14} /> Send to AI
        </button>
        <button
          onClick={onCancel}
          className="ml-auto text-[13px] text-[#003737]/50 hover:text-[#003737] transition-colors px-1.5 py-2"
        >
          Cancel
        </button>
      </div>
    </div>
  );
}

const CARD_CLS =
  "rounded-[14px] border border-white/20 bg-white text-[#003737] p-4 shadow-[0_6px_20px_-14px_rgba(0,0,0,0.4)] max-w-full";

/**
 * **Every card's action row is sticky to the bottom of the scroller** (BUG-060).
 *
 * The pending-card block is capped at `max-h-[70%]` of the chat column so a tall proposal
 * cannot squeeze the transcript to nothing — which means any card can scroll, and a row that
 * scrolls with it ends below the fold. The owner's screenshot caught exactly that: a card cut
 * straight across Accept and Edit, and a card you cannot answer is worse than no card.
 *
 * It was sticky on `ProposalCard` alone for a while, so the three cards most likely to be tall —
 * a multi-change review, a create-with-checklist — were the ones without it. The negative margins
 * cancel `CARD_CLS`'s `p-4` so the white strip reaches the card's edges.
 */
const CARD_ACTIONS_CLS = "sticky bottom-0 -mx-4 -mb-4 bg-white px-4 pb-4 pt-2";

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
              <Badge tone={p.status === "approved" ? "success" : "neutral"}>
                {p.status === "approved" ? "Added to goal" : "Dismissed"}
              </Badge>
              {p.status === "approved" && p.createdRef && (
                <button
                  onClick={() => onOpen(p.createdRef!)}
                  className="ml-auto inline-flex items-center gap-1.5 px-3 py-1.5 rounded-[9px] bg-[#005961] text-white text-[12.5px] font-semibold hover:bg-[#003737] transition-colors"
                >
                  <Ic path={PATHS.switch_} size={13} /> Open
                </button>
              )}
            </div>
          ) : (
            // Sticky, so Accept is reachable however tall the proposal is (BUG-060). A long
            // preview inside a short drawer pushed this row below the scroller's fold, and the
            // owner's screenshot caught a card cut straight across Accept and Edit — a card you
            // cannot answer is worse than no card.
            <div
              className={cn(CARD_ACTIONS_CLS, "mt-3.5 flex items-center gap-2")}
            >
              <button
                onClick={() => {
                  onResolve("approved");
                  onApprove(p);
                }}
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#005961] text-white text-[13px] font-semibold hover:bg-[#003737] transition-colors"
              >
                <Ic path={PATHS.check} size={14} /> Accept
              </button>
              <button
                onClick={() => setInstructing(true)}
                title="Ask the AI to change this proposal"
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-[9px] border border-[#DCDCDC] text-[#003737] text-[13px] font-medium hover:border-[#005961]/40 transition-colors"
              >
                <Ic path={PATHS.pencil} size={13} /> Edit
              </button>
              <button
                onClick={() => onResolve("rejected")}
                className="ml-auto text-[13px] text-[#003737]/50 hover:text-red-600 transition-colors px-1.5 py-2"
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
          ? "bg-[#005961] border-[#005961] text-white"
          : "border-[#D6D6D6] bg-white text-transparent",
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
        <Badge tone={p.status === "approved" ? "success" : "neutral"}>
          {p.status === "approved" ? "Added to goal" : "Dismissed"}
        </Badge>
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
        <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#005961]">
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
              "text-[14px] font-semibold text-[#003737] leading-[1.3] break-words [overflow-wrap:anywhere]",
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
          <span className="text-[14px] font-semibold text-[#003737] leading-[1.3]">
            Make it the active option
          </span>
        </button>
      </div>
      <button
        onClick={() => setInstructing(true)}
        className="inline-flex items-center gap-1.5 mt-3 text-[12.5px] text-[#003737]/55 hover:text-[#003737] transition-colors"
      >
        <Ic path={PATHS.sparkles} size={12} /> Type a change for the AI…
      </button>
      <div
        className={cn(
          CARD_ACTIONS_CLS,
          "mt-3.5 flex items-center gap-2 border-t border-[#F3F3F3] pt-3",
        )}
      >
        <button
          onClick={confirm}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-[9px] bg-[#005961] text-white text-[13px] font-semibold hover:bg-[#003737] transition-colors"
        >
          <Ic path={PATHS.check} size={14} /> Confirm
        </button>
        <button
          onClick={() => onResolve("rejected")}
          className="ml-auto text-[13px] text-[#003737]/50 hover:text-red-600 transition-colors px-1.5 py-2"
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
              ? "bg-[#005961]/10 text-[#005961]"
              : "bg-black/6 text-[#003737]/50",
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
        <span className="text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#005961]">
          {total} change{total > 1 ? "s" : ""}
        </span>
        <span className="ml-auto text-[11px] font-medium text-[#003737]/45 tabular-nums">
          {idx + 1} / {total}
        </span>
        <button
          onClick={dismissAll}
          aria-label="Dismiss all"
          className="text-[#003737]/35 hover:text-red-600 transition-colors p-0.5 -mr-0.5"
        >
          <X size={16} />
        </button>
      </div>
      <div className="h-[5px] rounded-full bg-[#005961]/12 overflow-hidden mb-3.5">
        <div
          className="h-full rounded-full bg-[#005961] transition-all duration-300"
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
                <div className="text-[14px] font-semibold text-[#003737] leading-[1.3] break-words [overflow-wrap:anywhere]">
                  {headline}
                </div>
                {/* For creates, fields live in their own checkboxes below — never restate
                    them here. Non-create changes keep their summary line. */}
                {detail && !isCreate && (
                  <div className="text-[12.5px] text-[#003737]/55 mt-0.5 break-words [overflow-wrap:anywhere]">
                    {detail}
                  </div>
                )}
                {/* Checklist shows its items as real checkboxes below — skip the count line. */}
                {isCreate && summary && !curItems && (
                  <div className="text-[12.5px] text-[#003737]/55 mt-0.5 break-words [overflow-wrap:anywhere]">
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
                className="self-start ml-[30px] inline-flex items-center gap-1.5 text-[12px] font-medium text-[#005961] hover:text-[#003737] transition-colors"
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
                <span className="text-[13.5px] font-semibold text-[#003737]">
                  Make it the active option
                </span>
              </button>
            )}
          </div>

          <button
            onClick={() => setInstructing(true)}
            className="inline-flex items-center gap-1.5 mt-3 text-[12.5px] text-[#003737]/55 hover:text-[#003737] transition-colors"
          >
            <Ic path={PATHS.sparkles} size={12} /> Type a change for the AI…
          </button>

          <div className="mt-3.5 flex items-center justify-between border-t border-[#F3F3F3] pt-3">
            <button
              onClick={() => setStep((s) => Math.max(0, s - 1))}
              disabled={idx === 0}
              className="inline-flex items-center gap-1 text-[13px] font-medium text-[#003737]/70 disabled:opacity-30 hover:text-[#003737] transition-colors"
            >
              <Ic path={PATHS.chevron} size={13} className="rotate-90" /> Back
            </button>
            {idx < total - 1 ? (
              <button
                onClick={() => setStep((s) => Math.min(total - 1, s + 1))}
                className="inline-flex items-center gap-1 text-[13px] font-semibold text-[#005961] hover:text-[#003737] transition-colors"
              >
                Next{" "}
                <Ic path={PATHS.chevron} size={13} className="-rotate-90" />
              </button>
            ) : (
              <span className="text-[12px] text-[#003737]/40">
                End of review
              </span>
            )}
          </div>

          <div className={cn(CARD_ACTIONS_CLS, "mt-3 flex flex-col gap-1.5")}>
            <button
              onClick={saveAll}
              disabled={includedCount === 0}
              className="w-full inline-flex items-center justify-center gap-1.5 px-3.5 py-2.5 rounded-[10px] bg-[#005961] text-white text-[13.5px] font-semibold hover:bg-[#003737] disabled:opacity-40 transition-colors"
            >
              <Ic path={PATHS.check} size={15} />
              {includedCount === total
                ? `Save all ${total}`
                : `Save ${includedCount} of ${total}`}
            </button>
            <button
              onClick={dismissAll}
              className="text-[12.5px] text-[#003737]/45 hover:text-red-600 transition-colors py-1"
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
        <span className="text-[13.5px] font-medium text-[#003737]/85 leading-[1.3] break-words [overflow-wrap:anywhere]">
          {label}
        </span>
      </button>
      {body && body.trim() && onExpand && (
        <button
          onClick={() => onExpand({ title: headline, body })}
          className="self-start ml-[30px] inline-flex items-center gap-1.5 text-[12px] font-medium text-[#005961] hover:text-[#003737] transition-colors"
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
            <span className="text-[13px] text-[#003737]/85 leading-[1.3] break-words [overflow-wrap:anywhere]">
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
      <Badge tone={p.status === "approved" ? "success" : "neutral"}>
        {p.status === "approved"
          ? isGoal
            ? "Goal created"
            : "Target added"
          : "Dismissed"}
      </Badge>
      {p.status === "approved" && p.createdRef && (
        <button
          onClick={() => onOpen(p.createdRef!)}
          className="ml-auto inline-flex items-center gap-1.5 px-3 py-1.5 rounded-[9px] bg-[#005961] text-white text-[12.5px] font-semibold hover:bg-[#003737] transition-colors"
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
          <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#005961]">
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
        <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#005961]">
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
              "text-[14px] font-semibold text-[#003737] leading-[1.3] break-words [overflow-wrap:anywhere]",
              !createOn && "opacity-45",
            )}
          >
            {isGoal ? "Create" : "Add"} «{headline}»
          </span>
        </button>
        {/* Numeric measure stays a one-line summary; a checklist shows its items as real,
            tickable checkboxes instead (so the count line would be redundant). */}
        {createSummary(p) && !items && (
          <div className="ml-[30px] -mt-1 text-[12.5px] text-[#003737]/55">
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
        className="inline-flex items-center gap-1.5 mt-3 text-[12.5px] text-[#003737]/55 hover:text-[#003737] transition-colors"
      >
        <Ic path={PATHS.sparkles} size={12} /> Type a change for the AI…
      </button>
      <div className="mt-3.5 flex items-center gap-2 border-t border-[#F3F3F3] pt-3">
        <button
          onClick={confirm}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-[9px] bg-[#005961] text-white text-[13px] font-semibold hover:bg-[#003737] transition-colors"
        >
          <Ic path={PATHS.check} size={14} />{" "}
          {isGoal ? "Create goal" : "Add target"}
        </button>
        <button
          onClick={() => onResolve("rejected")}
          className="ml-auto text-[13px] text-[#003737]/50 hover:text-red-600 transition-colors px-1.5 py-2"
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
        <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#005961]">
          <Ic path={meta.icon} size={12} /> {meta.label}
        </span>
      </div>
      <div className="font-['Playfair_Display'] text-[17px] font-semibold leading-[1.25] break-words [overflow-wrap:anywhere]">
        {headline}
      </div>
      {typeLine && (
        <p className="mt-1.5 text-[13.5px] leading-[1.5] text-[#003737]/60">
          {typeLine}
        </p>
      )}

      {settled ? (
        <CreateSettled p={p} isGoal={isGoal} onOpen={onOpen} />
      ) : (
        <div className={cn(CARD_ACTIONS_CLS, "mt-3.5 flex items-center gap-2")}>
          <button
            onClick={() => {
              onResolve("approved");
              onCreate(p);
            }}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-[9px] bg-[#005961] text-white text-[13px] font-semibold hover:bg-[#003737] transition-colors"
          >
            <Ic path={PATHS.check} size={14} />{" "}
            {isGoal ? "Create goal" : "Add target"}
          </button>
          <button
            onClick={() => onResolve("rejected")}
            className="ml-auto text-[13px] text-[#003737]/50 hover:text-red-600 transition-colors px-1.5 py-2"
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
 *
 * **Dark ink on a white plate, never white on the gradient** (owner, 2026-08-24). The
 * conversation fades from teal to `#F2FFFF`, so the `text-white/80` this used to carry was
 * legible at the top of a long chat and invisible at the bottom — which is exactly where a
 * just-answered card lands. The Android twin (`ResultSummary` in `AiChatScreen.kt`) already
 * drew it as chat ink `#003737` on a 72%-white pill with a Kale "Open"; that pair holds at
 * both ends of the gradient (≈10:1 on the teal, ≈13:1 near white) and is what the web copies
 * here. Nothing in this row may go back to white.
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
      <div className="inline-flex items-center gap-1.5 self-start text-[12.5px] text-[#003737]/60 bg-white/70 px-3 py-1.5 rounded-full">
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
            className="inline-flex items-center gap-2 self-start text-[12.5px] text-[#003737] bg-white/70 px-3 py-1.5 rounded-full max-w-full"
          >
            <Ic
              path={PATHS.check}
              size={12}
              className="shrink-0 text-[#0A8080]"
            />
            <span className="truncate">{headline}</span>
            {p.createdRef && (
              <button
                onClick={() => onOpen(p.createdRef!)}
                className="shrink-0 inline-flex items-center gap-1 font-semibold text-[#0A8080] hover:underline"
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
    <div className="absolute inset-0 z-40 flex items-end bg-[rgba(0,55,55,0.4)] backdrop-blur-[2px]">
      <div
        className="w-full bg-white text-[#003737] rounded-t-[22px] px-5 pt-6 pb-5"
        style={{ animation: "slideUp 0.3s cubic-bezier(0.2,0.8,0.2,1) both" }}
      >
        <span className="inline-flex items-center gap-1.5 text-[11px] uppercase tracking-[0.07em] font-bold text-[#005961]">
          <SproutArt size={15} /> GROW session
        </span>
        <h3 className="font-['Playfair_Display'] text-[22px] font-semibold mt-2.5 mb-1 leading-[1.18]">
          Focused time on a single goal
        </h3>
        <p className="text-[13.5px] text-[#003737]/60 mb-4 leading-[1.5]">
          A conversation without rush. I'll help you get clarity — the decisions
          stay yours.
        </p>
        <div className="grid grid-cols-4 gap-2 mb-3.5">
          {([15, 30, 45, 60] as const).map((m) => (
            <button
              key={m}
              onClick={() => setMins(m)}
              className={cn(
                "flex flex-col items-center py-2.5 rounded-xl border text-[#003737] transition-colors",
                mins === m
                  ? "border-[#005961] bg-[#E5F4F3] text-[#005961]"
                  : "border-[#E5E5E5] hover:border-[#005961]/40",
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
          className="w-full px-3.5 py-3 border border-[#E5E5E5] rounded-xl text-[14px] bg-white text-[#003737] mb-3.5 outline-none focus:border-[#005961] focus:ring-2 focus:ring-[#005961]/12 transition"
        />
        <button
          onClick={() => onStart(mins, focus)}
          className="w-full flex items-center justify-center gap-2 py-3.5 rounded-xl bg-[#005961] text-white text-[14.5px] font-semibold hover:bg-[#003737] transition-colors"
        >
          Start session · {mins} min
        </button>
        <button
          onClick={onCancel}
          className="block mx-auto mt-2 text-[13px] text-[#003737]/50 hover:text-[#003737] transition-colors py-1.5"
        >
          Cancel
        </button>
      </div>
      <style>{`@keyframes slideUp { from { transform: translateY(34px); opacity: 0.25; } to { transform: translateY(0); opacity: 1; } }`}</style>
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
    <div className="rounded-[14px] border border-white/20 bg-white text-[#003737] p-4 shadow-[0_6px_20px_-14px_rgba(0,0,0,0.4)]">
      <span className="inline-flex items-center gap-1.5 text-[10.5px] uppercase tracking-[0.07em] font-semibold text-[#005961]">
        <Ic path={PATHS.brain} size={12} className="text-[#005961]" /> Session
        wrap-up
      </span>
      <p className="mt-2 text-[13.5px] leading-[1.5] text-[#003737]/60">
        {memory
          ? "This is what will be saved as the session memory — next time we'll continue from it."
          : "Save what I learned about this goal? Next time we'll continue instead of starting from scratch."}
      </p>
      {memory && (
        <div
          className={cn(
            "mt-2.5 rounded-[9px] border border-[#E5E5E5] bg-[#FFFAF2] px-3 py-2.5 max-h-44 overflow-y-auto text-[12.5px] leading-[1.55] text-[#003737]/85 whitespace-pre-wrap select-text",
            revising && "opacity-50",
          )}
        >
          {memory}
        </div>
      )}
      {memory && (
        <div className="mt-2 flex items-center gap-2 rounded-[9px] border border-[#E5E5E5] bg-white px-3 py-1.5 focus-within:border-[#005961] transition-colors">
          {revising ? (
            <span className="h-3.5 w-3.5 shrink-0 rounded-full border-2 border-[#005961]/30 border-t-[#005961] animate-spin" />
          ) : (
            <Ic
              path={PATHS.pencil}
              size={13}
              className="shrink-0 text-[#003737]/40"
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
            className="flex-1 bg-transparent outline-none text-[13px] text-[#003737] placeholder:text-[#003737]/35 min-h-[30px] disabled:opacity-60"
          />
          {reviseDraft.trim() && !revising && (
            <button
              onClick={sendRevise}
              className="shrink-0 text-[12.5px] font-semibold text-[#005961] hover:text-[#003737] px-1"
            >
              Revise
            </button>
          )}
        </div>
      )}
      {proposals > 0 && (
        <div className="mt-2.5 flex items-center gap-2 px-3 py-2 rounded-[9px] bg-[#E5F4F3] text-[#005961] text-[12.5px]">
          <Ic path={PATHS.target} size={12} /> {proposals} proposal
          {proposals === 1 ? "" : "s"} still awaiting your decision
        </div>
      )}
      <div className="mt-3.5 flex items-center gap-2">
        <button
          onClick={onSave}
          disabled={revising}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#005961] text-white text-[13px] font-semibold hover:bg-[#003737] transition-colors disabled:opacity-50"
        >
          <Ic path={PATHS.check} size={14} /> Save memory
        </button>
        <button
          onClick={onDiscard}
          className="ml-auto text-[13px] text-[#003737]/50 hover:text-[#003737] transition-colors px-1.5"
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
      className="absolute inset-0 z-45 flex items-end bg-[rgba(0,55,55,0.42)] backdrop-blur-[2px]"
      onClick={onClose}
    >
      {/* The app's sheet shape (BUG-061): a fixed Kale head, and only the body scrolls.
          It used to be one `overflow-y-auto` container with a hand-drawn grab handle and a
          white head, so the title, the close button and — the part with consequence — the
          sentence saying what happens to a pasted API key all scrolled away as soon as the
          user reached the provider they wanted. `max-h-[88%]` also made it the shortest
          drawer in the app, since that 88% was of the panel rather than of the screen — the
          owner's "он короче чем обычный drawer — думаю это неверно".

          `sheet-h-88` is the owner's number (2026-08-28), measured against the SCREEN like
          every other sheet. Note what that means here: this sheet is `absolute inset-0` inside
          the 92 %-tall chat drawer, so 88 % of the screen covers all but ~36 px of it. That
          thin teal strip is the whole of the coach that shows through, and it is meant to read
          as one sheet stacked on another rather than as a gap. Making it 88 % of the DRAWER
          instead would leave the coach's head visible, but it is also exactly the "too short"
          this sheet was reported for. */}
      <div
        className="sheet-h-88 flex w-full min-h-0 flex-col overflow-hidden bg-white text-[#003737] rounded-t-[22px]"
        onClick={(e) => e.stopPropagation()}
        style={{
          animation: "slideUp 0.3s cubic-bezier(0.2,0.8,0.2,1) both",
        }}
      >
        <SheetHead title="AI providers" onClose={onClose} />
        <div className="min-h-0 flex-1 overflow-y-auto px-5 pt-4 pb-5">
          <p className="text-[13.5px] text-[#003737]/60 mb-4 leading-[1.5]">
            Keys are stored encrypted on your account. Keep several connected
            and switch anytime.
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
                      ? "border-[#005961] bg-[#E5F4F3]/50"
                      : "border-[#E5E5E5]",
                  )}
                >
                  {/* Header row */}
                  <div className="flex items-center justify-between gap-2">
                    <div>
                      <span className="font-semibold text-[15px]">
                        {p.vendor}
                      </span>
                      <span className="ml-2 text-[12px] text-[#003737]/50">
                        {p.context}
                      </span>
                    </div>
                    {/* Green, not teal: "Active" sat on a teal-outlined card and vanished into its
                      own frame. Success reads as "this one is working". */}
                    {isActive ? (
                      <Badge tone="success">Active</Badge>
                    ) : p.connected ? (
                      <button
                        onClick={() => onActivate(p.id)}
                        className="inline-flex items-center gap-1 text-[12px] font-medium text-[#003737] border border-[#E5E5E5] px-2.5 py-1 rounded-lg hover:border-[#005961]/40 transition-colors"
                      >
                        <Ic path={PATHS.switch_} size={12} /> Use this
                      </button>
                    ) : (
                      <span className="text-[12px] text-[#003737]/40">
                        Not connected
                      </span>
                    )}
                  </div>

                  {/* Model selector — only when connected */}
                  {p.connected && editing !== p.id && (
                    <div className="relative mt-2.5">
                      <button
                        onClick={() => handleDropdownToggle(p.id, p.connected)}
                        className="w-full flex items-center justify-between gap-2 px-3 py-2 rounded-lg border border-[#E5E5E5] bg-white hover:border-[#005961]/40 transition-colors text-left"
                      >
                        <span className="text-[13px] text-[#003737] font-mono truncate">
                          {p.activeModel || "Select model"}
                        </span>
                        <Ic
                          path={PATHS.chevron}
                          size={14}
                          className={cn(
                            "shrink-0 text-[#003737]/50 transition-transform duration-150",
                            dropOpen && "rotate-180",
                          )}
                        />
                      </button>

                      {dropOpen && (
                        <div className="absolute top-full left-0 right-0 z-50 mt-1 bg-white border border-[#E5E5E5] rounded-xl shadow-lg overflow-hidden">
                          {isLoadingMdl ? (
                            <div className="px-3 py-3 text-[13px] text-[#003737]/50 text-center">
                              Loading models…
                            </div>
                          ) : (fetchedModels ?? p.models).length === 0 ? (
                            <div className="px-3 py-3 text-[13px] text-[#003737]/50 text-center">
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
                                      ? "bg-[#E5F4F3] text-[#005961] font-semibold"
                                      : "text-[#003737] hover:bg-[#F4F4F3]",
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
                      <span className="inline-flex items-center gap-1.5 text-[12px] font-mono text-[#003737]/50">
                        <Ic path={PATHS.shield} size={12} /> {p.keyHint}
                      </span>
                      <button
                        onClick={() => {
                          setEditing(p.id);
                          setKeyVal("");
                          setShowKey(false);
                          setOpenDropdown(null);
                        }}
                        className="text-[12px] text-[#005961] hover:underline"
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
                      className="mt-2.5 inline-flex items-center gap-1.5 text-[13px] text-[#005961] hover:text-[#003737] font-medium"
                    >
                      <Ic path={PATHS.plus} size={14} /> Connect a key
                    </button>
                  )}

                  {/* Key input form */}
                  {editing === p.id && (
                    <div className="mt-3">
                      <div className="flex items-center gap-2 px-3 py-1 border-2 border-[#005961] rounded-xl bg-white shadow-[0_0_0_3px_rgba(0,89,97,0.12)]">
                        <input
                          type={showKey ? "text" : "password"}
                          value={keyVal}
                          onChange={(e) => setKeyVal(e.target.value)}
                          placeholder={
                            p.keyPrefix ? `${p.keyPrefix}…` : "API key"
                          }
                          autoFocus
                          className="flex-1 border-none outline-none font-mono text-[13.5px] text-[#003737] bg-transparent py-1.5 tracking-[0.02em]"
                        />
                        <button
                          onClick={() => setShowKey((s) => !s)}
                          className="bg-black/5 text-[#003737]/60 text-[11.5px] font-semibold px-2.5 py-1.5 rounded-lg"
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
                          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#005961] text-white text-[13px] font-semibold hover:bg-[#003737] disabled:opacity-40 transition-colors"
                        >
                          <Ic path={PATHS.check} size={14} /> Save &amp;
                          activate
                        </button>
                        <button
                          onClick={() => setEditing(null)}
                          className="text-[13px] text-[#003737]/50 hover:text-[#003737] px-2 transition-colors"
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
          <div className="mt-5 pt-4 border-t border-[#E5E5E5]">
            <span className="inline-flex items-center gap-1.5 text-[11px] uppercase tracking-[0.07em] font-bold text-[#005961]">
              <Ic path={PATHS.key} size={13} className="text-[#005961]" /> Web
              search
            </span>
            <p className="text-[12.5px] text-[#003737]/60 mt-1 mb-2.5 leading-[1.5]">
              Add a Tavily key (tavily.com) to let the assistant search the web.
              Optional.
            </p>
            <div className="border rounded-xl p-3.5 border-[#E5E5E5]">
              <div className="flex items-center justify-between gap-2">
                <span className="font-semibold text-[15px]">Tavily</span>
                {tavily.connected ? (
                  <Badge tone="success">Connected</Badge>
                ) : (
                  <span className="text-[12px] text-[#003737]/40">
                    Not connected
                  </span>
                )}
              </div>

              {tavily.connected && !editingTavily && (
                <div className="flex items-center justify-between mt-2.5">
                  <span className="inline-flex items-center gap-1.5 text-[12px] font-mono text-[#003737]/50">
                    <Ic path={PATHS.shield} size={12} /> {tavily.hint}
                  </span>
                  <button
                    onClick={() => {
                      setEditingTavily(true);
                      setTavilyVal("");
                    }}
                    className="text-[12px] text-[#005961] hover:underline"
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
                  className="mt-2.5 inline-flex items-center gap-1.5 text-[13px] text-[#005961] hover:text-[#003737] font-medium"
                >
                  <Ic path={PATHS.plus} size={14} /> Connect a key
                </button>
              )}

              {editingTavily && (
                <div className="mt-3">
                  <div className="flex items-center gap-2 px-3 py-1 border-2 border-[#005961] rounded-xl bg-white shadow-[0_0_0_3px_rgba(0,89,97,0.12)]">
                    <input
                      type="password"
                      value={tavilyVal}
                      onChange={(e) => setTavilyVal(e.target.value)}
                      placeholder="tvly-…"
                      autoFocus
                      className="flex-1 border-none outline-none font-mono text-[13.5px] text-[#003737] bg-transparent py-1.5 tracking-[0.02em]"
                    />
                  </div>
                  <div className="flex items-center gap-2 mt-2.5">
                    <button
                      disabled={!tavilyVal.trim()}
                      onClick={() => {
                        onSaveTavily(tavilyVal.trim());
                        setEditingTavily(false);
                      }}
                      className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[9px] bg-[#005961] text-white text-[13px] font-semibold hover:bg-[#003737] disabled:opacity-40 transition-colors"
                    >
                      <Ic path={PATHS.check} size={14} /> Save
                    </button>
                    <button
                      onClick={() => setEditingTavily(false)}
                      className="text-[13px] text-[#003737]/50 hover:text-[#003737] px-2 transition-colors"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>

          <p className="mt-4 flex items-center gap-1.5 text-[12px] text-[#003737]/40">
            <Ic path={PATHS.shield} size={12} /> Keys never leave your account
            and are encrypted at rest.
          </p>
        </div>
      </div>
      <style>{`@keyframes slideUp { from { transform: translateY(34px); opacity: 0.25; } to { transform: translateY(0); opacity: 1; } }`}</style>
    </div>
  );
}

// ── End confirm dialog ─────────────────────────────────────────────────────

/**
 * Ending early is two different intentions, and conflating them was wrong: a
 * user who disliked how the session went wants out, not a ceremony over it
 * (owner, 2026-08-22). So the wrap-up is offered, and quitting outright is a
 * peer of it rather than something hidden behind a cancel.
 */
function EndConfirmDialog({
  remainingLabel,
  onConfirm,
  onQuit,
  onCancel,
}: {
  remainingLabel: string;
  onConfirm: () => void;
  onQuit: () => void;
  onCancel: () => void;
}) {
  return (
    <div
      className="absolute inset-0 z-45 flex items-end bg-[rgba(0,55,55,0.42)] backdrop-blur-[2px]"
      onClick={onCancel}
    >
      <div
        className="w-full bg-white text-[#003737] rounded-t-[22px] px-5 pt-6 pb-5"
        onClick={(e) => e.stopPropagation()}
        style={{ animation: "slideUp 0.3s cubic-bezier(0.2,0.8,0.2,1) both" }}
      >
        <h3 className="font-['Playfair_Display'] text-[22px] font-semibold leading-[1.18] mb-2">
          End the session early?
        </h3>
        <p className="text-[13.5px] text-[#003737]/60 leading-[1.5] mb-4">
          {remainingLabel && `There's still ${remainingLabel} left. `}How would
          you like to leave it?
        </p>
        <div className="flex flex-col gap-2">
          <button
            onClick={onConfirm}
            className="w-full py-3 rounded-xl bg-[#005961] text-white text-[14px] font-semibold hover:bg-[#003737] transition-colors"
          >
            Close it properly
          </button>
          <p className="text-[12px] text-[#003737]/45 leading-[1.45] -mt-0.5 mb-1">
            A short, honest close: what we did and didn&apos;t get to, and
            whether to keep any of it.
          </p>
          <button
            onClick={onQuit}
            className="w-full py-3 rounded-xl border border-[#E5E5E5] text-[#003737] text-[14px] font-medium hover:border-[#005961]/40 transition-colors"
          >
            Just close, save nothing
          </button>
          <button
            onClick={onCancel}
            className="w-full py-2.5 text-[13.5px] text-[#003737]/50 hover:text-[#003737] transition-colors"
          >
            Keep going
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Thinking loader ──────────────────────────────────────────────────────────

/**
 * The "waiting for the answer" indicator: three dots fading in sequence, in the chat's own deep
 * teal (owner, 2026-08-17 — the source SVG's blue swapped for `#005961`). Shown while a reply is
 * streaming but has produced no text yet.
 */
function ThinkingDots({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      width="30"
      height="15"
      fill="#005961"
      viewBox="0 0 24 24"
      xmlns="http://www.w3.org/2000/svg"
      role="status"
      aria-label="Thinking"
    >
      <circle cx="4" cy="12" r="3" opacity="1">
        <animate
          id="spinner_qYjJ"
          begin="0;spinner_t4KZ.end-0.25s"
          attributeName="opacity"
          dur="0.75s"
          values="1;.2"
          fill="freeze"
        />
      </circle>
      <circle cx="12" cy="12" r="3" opacity=".4">
        <animate
          begin="spinner_qYjJ.begin+0.15s"
          attributeName="opacity"
          dur="0.75s"
          values="1;.2"
          fill="freeze"
        />
      </circle>
      <circle cx="20" cy="12" r="3" opacity=".3">
        <animate
          id="spinner_t4KZ"
          begin="spinner_qYjJ.begin+0.3s"
          attributeName="opacity"
          dur="0.75s"
          values="1;.2"
          fill="freeze"
        />
      </circle>
    </svg>
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
          ? "text-[#003737]/55 hover:text-[#003737]"
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

/** Files the composer accepts as direct attachments (BUG-017). */
const ATTACH_ACCEPT =
  "image/png,image/jpeg,image/webp,image/gif,application/pdf," +
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document,.docx";
const ATTACH_MAX_BYTES = 5 * 1024 * 1024; // 5 MB — applies to PDF/DOCX (not downscaled)
// Images are downscaled+recompressed before storage, so we accept a larger
// original (a phone photo is easily 3-8 MB) but still refuse an absurd one that
// could OOM the decode on a constrained mobile browser.
const IMAGE_MAX_INPUT_BYTES = 25 * 1024 * 1024;
// Longest edge we keep. Vision models downscale to ~1.5-2k px anyway, so a phone
// photo (4000px) shrinks ~6x — cutting a multi-MB image to a few hundred KB and
// avoiding the out-of-memory crash on mobile.
const IMAGE_MAX_DIM = 1600;
const IMAGE_JPEG_QUALITY = 0.8;
const ATTACH_MAX_COUNT = 6; // matches the backend ChatRequest cap

/** Reads a file to a base64 data URL (used for PDF/DOCX, which aren't downscaled). */
function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

/**
 * Reads an image's pixel dimensions straight from its file header (PNG IHDR / JPEG SOF), reading
 * only the first bytes — no full decode. Lets {@link downscaleImage} ask the browser to decode a
 * camera photo directly at reduced resolution instead of materialising the full-size bitmap first
 * (the peak that OOMs constrained phones). Returns null for formats it can't cheaply measure.
 */
async function readImageSize(
  file: File,
): Promise<{ w: number; h: number } | null> {
  try {
    const buf = await file.slice(0, 64 * 1024).arrayBuffer();
    const b = new Uint8Array(buf);
    const dv = new DataView(buf);
    // PNG: 8-byte signature, then IHDR (width @16, height @20, big-endian).
    if (b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47) {
      return { w: dv.getUint32(16), h: dv.getUint32(20) };
    }
    // JPEG: FF D8, then walk segments to a Start-Of-Frame marker (FFC0–FFCF, minus DHT/JPG/DAC).
    if (b[0] === 0xff && b[1] === 0xd8) {
      let o = 2;
      while (o + 9 < b.length) {
        if (b[o] !== 0xff) {
          o++;
          continue;
        }
        const marker = b[o + 1];
        if (
          marker >= 0xc0 &&
          marker <= 0xcf &&
          marker !== 0xc4 &&
          marker !== 0xc8 &&
          marker !== 0xcc
        ) {
          return { h: dv.getUint16(o + 5), w: dv.getUint16(o + 7) };
        }
        const len = dv.getUint16(o + 2);
        if (len < 2) break;
        o += 2 + len;
      }
    }
  } catch {
    /* fall through */
  }
  return null;
}

/**
 * Downscales + recompresses an image to a JPEG data URL bounded by {@link IMAGE_MAX_DIM}. This is
 * what keeps a large phone photo from spiking memory: instead of holding a multi-MB base64 string
 * (in state, the message, and the request body at once) we keep a few-hundred-KB JPEG. When the
 * header gives us the dimensions we decode straight to the reduced size ({@code resizeWidth}/
 * {@code resizeHeight}) so the full bitmap is never materialised — the key to not OOMing a camera
 * capture on mobile. Returns the data URL and its MIME.
 */
async function downscaleImage(
  file: File,
): Promise<{ dataUrl: string; mime: string }> {
  const dims = await readImageSize(file);
  let bitmap: ImageBitmap;
  try {
    // `imageOrientation: "from-image"` is stated rather than assumed: a phone photo carries its
    // rotation in EXIF, and a sideways picture is markedly harder to read — for the user and for
    // OCR alike (BUG-027). Browsers differ on the default; this pins it.
    //
    // Only ONE axis is constrained, because resizing happens AFTER that rotation: an EXIF
    // quarter-turn swaps the axes, so passing both (computed from the pre-rotation header dims)
    // would squash the picture. Capping the longer axis keeps the aspect ratio and errs towards
    // more detail; the draw-time scale below still enforces the real cap.
    if (dims && Math.max(dims.w, dims.h) > IMAGE_MAX_DIM) {
      const landscape = dims.w >= dims.h;
      bitmap = await createImageBitmap(file, {
        ...(landscape
          ? { resizeWidth: IMAGE_MAX_DIM }
          : { resizeHeight: IMAGE_MAX_DIM }),
        resizeQuality: "medium",
        imageOrientation: "from-image",
      });
    } else {
      bitmap = await createImageBitmap(file, {
        imageOrientation: "from-image",
      });
    }
  } catch {
    // Couldn't decode. Keeping the raw bytes is fine for a small file, but for a large one it
    // would only pile more base64 onto the memory pressure that likely caused this — refuse
    // instead, so the composer shows an error rather than risking an OOM.
    if (file.size > ATTACH_MAX_BYTES) {
      throw new Error("Image too large to process on this device");
    }
    return { dataUrl: await readFileAsDataUrl(file), mime: file.type };
  }
  try {
    // A draw-time scale is the safety net for the path where the header was unreadable and the
    // bitmap came back full-size; when we pre-resized above, the bitmap is already ≤ IMAGE_MAX_DIM
    // so this is ~1:1.
    const scale = Math.min(
      1,
      IMAGE_MAX_DIM / Math.max(bitmap.width, bitmap.height),
    );
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("no 2d context");
    ctx.drawImage(bitmap, 0, 0, width, height);
    const dataUrl = canvas.toDataURL("image/jpeg", IMAGE_JPEG_QUALITY);
    canvas.width = 0; // release the backing bitmap promptly
    canvas.height = 0;
    return { dataUrl, mime: "image/jpeg" };
  } finally {
    bitmap.close?.();
  }
}

function isAttachableType(mime: string, name: string): boolean {
  if (mime.startsWith("image/")) {
    return ["image/png", "image/jpeg", "image/webp", "image/gif"].includes(
      mime,
    );
  }
  return (
    mime === "application/pdf" ||
    mime ===
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
    name.toLowerCase().endsWith(".docx")
  );
}

function Composer({
  onSend,
  placeholder,
  busy,
  onStop,
  initialValue,
  onDraftChange,
  leftAction,
  allowAttachments,
  draftScope,
  attachResources,
  resolveAttachmentOpen,
}: {
  onSend: (text: string, attachments?: ChatAttachment[]) => void;
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
  /** Show the paperclip to attach files directly to the message (regular chat). */
  allowAttachments?: boolean;
  /**
   * Where an unsent draft is kept so it survives a page reload — the chat's scope key, or
   * undefined not to keep one at all.
   *
   * A GROW session passes undefined on purpose: the session is ephemeral by design, and half a
   * sentence typed into a coaching session has no meaning once the session is over.
   */
  draftScope?: string;
  /**
   * This goal's saved resources, offered as a second attach source (BUG-030). When present and
   * non-empty, the paperclip becomes a small menu — "Choose a file" / "From resources", the same
   * two rows Android's menu carries — instead of
   * opening the file dialog straight away.
   */
  attachResources?: {
    id: string;
    label: string;
    type: string;
    typeLabel: string;
    mime?: string;
  }[];
  /**
   * What a chip opens, decided by the panel (it holds the goal, and a resource chip is only
   * an id). Returns null when there is nothing to open, and the chip then renders as plain
   * text rather than as a button that does nothing. See `attachmentOpener`.
   */
  resolveAttachmentOpen?: (a: ChatAttachment) => (() => void) | null;
}) {
  const [v, setV] = useState(initialValue ?? "");
  const [attachments, setAttachments] = useState<ChatAttachment[]>([]);
  const [attachError, setAttachError] = useState("");
  // Which attach surface is open: the source menu, or the resource picker. Null = closed.
  const [attachMenu, setAttachMenu] = useState<null | "sources">(null);
  // The picker is a sheet now, not a second page of the attach dropdown — Android's is,
  // and the two surfaces draw the same card (CLAUDE.md, Design 3e).
  const [resourceSheet, setResourceSheet] = useState(false);
  // Reads in flight (FileReader is async). Send waits for these to reach 0 so a
  // file picked just before hitting Enter isn't silently dropped.
  const [reading, setReading] = useState(0);
  // Synchronous slot accounting (committed + in-flight): a ref, not state, so two
  // rapid picks can't both read a stale count and blow past ATTACH_MAX_COUNT.
  const claimedRef = useRef(0);
  const ref = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  // Which stored blob each byte-carrying chip came from / went to, so re-saving on every
  // keystroke doesn't rewrite several megabytes. Weak: an entry dies with its chip.
  const blobKeysRef = useRef(new WeakMap<ChatAttachment, string>());
  // The scope whose draft has been read back. Writing before the read lands would save an
  // empty composer over the very draft being restored, so the save waits for this.
  const restoredRef = useRef<string | null>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 128) + "px";
  }, [v]);

  // Restore what was half-composed here before the page went away (BUG-049). Anything the
  // user has already typed or attached in the meantime wins — this only ever fills a gap.
  useEffect(() => {
    if (!draftScope) return;
    let cancelled = false;
    restoredRef.current = null;
    loadComposerDraft(draftScope).then(({ text, attachments: kept, keys }) => {
      if (cancelled) return;
      keys.forEach(([a, k]) => blobKeysRef.current.set(a, k));
      if (text) setV((cur) => cur || text);
      if (kept.length) {
        setAttachments((cur) => (cur.length ? cur : kept));
        claimedRef.current = Math.max(claimedRef.current, kept.length);
      }
      restoredRef.current = draftScope;
    });
    return () => {
      cancelled = true;
    };
  }, [draftScope]);

  // Write it down as it is typed. Debounced: a draft is worth keeping, not worth a storage
  // write per keystroke.
  useEffect(() => {
    if (!draftScope || restoredRef.current !== draftScope) return;
    const t = setTimeout(() => {
      void saveComposerDraft(draftScope, v, attachments, blobKeysRef.current);
    }, 400);
    return () => clearTimeout(t);
  }, [draftScope, v, attachments]);

  const resetAttachments = () => {
    setAttachments([]);
    setAttachError("");
    setAttachMenu(null);
    setResourceSheet(false);
    setReading(0);
    claimedRef.current = 0;
  };

  const addFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const errors = new Set<string>(); // distinct reasons, so one bad file doesn't hide another
    for (const f of Array.from(files)) {
      if (claimedRef.current >= ATTACH_MAX_COUNT) {
        errors.add(`You can attach up to ${ATTACH_MAX_COUNT} files.`);
        break;
      }
      if (!isAttachableType(f.type, f.name)) {
        errors.add("Only images, PDF, or DOCX files can be attached.");
        continue;
      }
      const isImage = f.type.startsWith("image/");
      // Images are downscaled below, so they may exceed the 5 MB doc cap (a phone
      // photo does) — only reject a truly huge one. PDF/DOCX keep the 5 MB cap.
      if (isImage && f.size > IMAGE_MAX_INPUT_BYTES) {
        errors.add("That image is too large.");
        continue;
      }
      if (!isImage && f.size > ATTACH_MAX_BYTES) {
        errors.add("Each file must be 5 MB or smaller.");
        continue;
      }
      claimedRef.current += 1;
      setReading((n) => n + 1);
      // Downscale images (keeps memory low on phones); read PDF/DOCX as-is.
      const load = isImage
        ? downscaleImage(f)
        : readFileAsDataUrl(f).then((dataUrl) => ({ dataUrl, mime: f.type }));
      load
        .then(({ dataUrl, mime }) => {
          setReading((n) => n - 1);
          setAttachments((prev) => [...prev, { name: f.name, mime, dataUrl }]);
        })
        .catch(() => {
          setReading((n) => n - 1);
          claimedRef.current = Math.max(0, claimedRef.current - 1);
          setAttachError(`Couldn't read "${f.name}". Try again.`);
        });
    }
    setAttachError(errors.size ? Array.from(errors).join(" ") : "");
    if (fileRef.current) fileRef.current.value = ""; // allow re-picking the same file
  };

  const removeAttachment = (idx: number) => {
    setAttachments((prev) => prev.filter((_, i) => i !== idx));
    claimedRef.current = Math.max(0, claimedRef.current - 1);
  };

  // Attach one of the goal's saved resources by id (BUG-030): no bytes travel from here — the
  // server inlines what it already holds. It rides the same count cap and chip UI as a file, and
  // a resource can't be attached twice.
  const addResource = (r: { id: string; label: string; mime?: string }) => {
    const resourceId = Number(r.id);
    if (!Number.isFinite(resourceId)) return;
    if (attachments.some((a) => a.resourceId === resourceId)) return;
    if (attachments.length + reading >= ATTACH_MAX_COUNT) {
      setAttachError(`You can attach up to ${ATTACH_MAX_COUNT} files.`);
      return;
    }
    claimedRef.current += 1;
    setAttachments((prev) => [
      ...prev,
      { name: r.label, mime: r.mime ?? "", resourceId },
    ]);
    setAttachMenu(null);
    setResourceSheet(false);
  };

  const hasResources = (attachResources?.length ?? 0) > 0;
  const attachDisabled =
    busy || attachments.length + reading >= ATTACH_MAX_COUNT;

  // Open the file dialog directly when there are no resources to choose from; otherwise offer the
  // two-source menu.
  const onPaperclip = () => {
    if (hasResources) setAttachMenu((m) => (m ? null : "sources"));
    else fileRef.current?.click();
  };

  const fire = () => {
    const t = v.trim();
    // Send when there is text OR at least one attachment — a photo or resource on its own is a
    // valid message (the server supplies a default prompt). Wait for any in-flight file reads.
    if ((!t && attachments.length === 0) || reading > 0) return;
    onSend(t, attachments.length ? attachments : undefined);
    setV("");
    resetAttachments();
    onDraftChange?.("");
    // The message has left; what was kept for a reload would otherwise come back as a
    // duplicate of something already in the transcript.
    if (draftScope) void clearComposerDraft(draftScope);
  };

  // Layout mirrors the Claude app composer: the textarea spans the full width
  // on top; below it a bottom row with quick actions on the left (e.g. "Start
  // GROW session", like the app's "</> Code" chip) and Send/Stop on the right.
  return (
    // No ground of its own: the gradient behind the whole chat column shows through, so the white
    // card is the ONLY container the user can see (owner, 2026-08-17).
    <div className="px-3 pb-3 pt-1 sm:px-4 sm:pb-4">
      {/* A white card, like every message bubble — the field belongs in a container; it just sits
          on the light chat area, not on a block of its own. */}
      <div className="rounded-2xl border border-black/10 bg-white px-3 pt-2.5 pb-2 shadow-sm transition-[border-color,box-shadow] focus-within:border-[#0A8080] focus-within:ring-[3px] focus-within:ring-[#0A8080]/20">
        {attachments.length > 0 && (
          <div className="flex flex-wrap gap-1.5 pb-2">
            {attachments.map((a, i) => {
              // A chip opens whatever it is — a photo, a note, a contact — or nothing at
              // all if there is nothing to show. Same rule as a sent message's chips, so an
              // attachment behaves identically either side of Send. The ✕ removes.
              const open = resolveAttachmentOpen?.(a) ?? null;
              const label = (
                <>
                  <Paperclip className="h-3 w-3 shrink-0 opacity-60" />
                  <span className="truncate">{a.name}</span>
                </>
              );
              return (
                <span
                  key={i}
                  className="inline-flex max-w-[220px] items-center gap-1.5 rounded-lg border border-[#003737]/15 bg-[#003737]/[0.04] pl-2 pr-1 py-1 text-[12px] text-[#003737]"
                >
                  {open ? (
                    <button
                      type="button"
                      onClick={open}
                      aria-label={`Open ${a.name}`}
                      className="inline-flex min-w-0 items-center gap-1.5 cursor-pointer"
                    >
                      {label}
                    </button>
                  ) : (
                    <span className="inline-flex min-w-0 items-center gap-1.5">
                      {label}
                    </span>
                  )}
                  <button
                    type="button"
                    onClick={() => removeAttachment(i)}
                    aria-label={`Remove ${a.name}`}
                    className="shrink-0 grid h-4 w-4 place-items-center rounded-full text-[#003737]/50 hover:bg-[#003737]/10 hover:text-[#003737]"
                  >
                    <X className="h-3 w-3" />
                  </button>
                </span>
              );
            })}
          </div>
        )}
        {attachError && (
          <p className="pb-1.5 text-[12px] text-[#EF523C]" role="alert">
            {attachError}
          </p>
        )}
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
          className="w-full bg-transparent resize-none outline-none text-[14.5px] leading-[1.45] text-[#003737] placeholder:text-[#003737]/40 max-h-32 px-1 py-1"
        />
        <div className="flex items-center gap-2 pt-1">
          {allowAttachments && (
            <>
              <input
                ref={fileRef}
                type="file"
                multiple
                accept={ATTACH_ACCEPT}
                onChange={(e) => addFiles(e.target.files)}
                className="hidden"
              />
              <div className="relative shrink-0">
                <button
                  type="button"
                  onClick={onPaperclip}
                  disabled={attachDisabled}
                  className="grid h-8 w-8 place-items-center rounded-lg text-[#005961] hover:bg-[#005961]/10 disabled:opacity-40 transition-colors"
                  title={
                    hasResources
                      ? "Attach a file or a resource"
                      : "Attach a file (image, PDF, or DOCX)"
                  }
                  aria-label="Attach"
                  aria-haspopup={hasResources ? "menu" : undefined}
                  aria-expanded={hasResources ? attachMenu !== null : undefined}
                >
                  <Paperclip className="h-4 w-4" />
                </button>

                <ResourceAttachSheet
                  open={resourceSheet}
                  onOpenChange={setResourceSheet}
                  resources={attachResources ?? []}
                  attachedIds={attachments
                    .map((x) => x.resourceId)
                    .filter((x): x is number => typeof x === "number")}
                  onPick={addResource}
                />

                {attachMenu !== null && (
                  <>
                    {/* click-away layer */}
                    <div
                      className="fixed inset-0 z-40"
                      onClick={() => setAttachMenu(null)}
                    />
                    <div className="absolute bottom-10 left-0 z-50 w-64 rounded-md border border-border bg-white p-1 shadow-lg">
                      {attachMenu === "sources" ? (
                        <>
                          <button
                            type="button"
                            onClick={() => {
                              setAttachMenu(null);
                              fileRef.current?.click();
                            }}
                            className="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm text-foreground hover:bg-[#0A8080]/10"
                          >
                            <Paperclip className="h-4 w-4 opacity-70" />
                            {/* Android's wording for the same action (`AiChatScreen.kt`). */}
                            Choose a file
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              setAttachMenu(null);
                              setResourceSheet(true);
                            }}
                            className="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm text-foreground hover:bg-[#0A8080]/10"
                          >
                            {/* **The Android row, word for word and glyph for glyph** (owner,
                                2026-08-17): "From resources" with Gravity's folder-open. The web
                                said "From this goal's resources" over a copy mark, so the same
                                action read as two different things on the two surfaces. */}
                            <Ic
                              path={PATHS.folderOpen}
                              size={16}
                              className="opacity-70"
                            />
                            From resources
                          </button>
                        </>
                      ) : null}
                    </div>
                  </>
                )}
              </div>
            </>
          )}
          {leftAction}
          <span className="flex-1" />
          {busy ? (
            <button
              onClick={onStop}
              className="w-9 h-9 shrink-0 grid place-items-center rounded-full bg-[#005961] text-white hover:bg-[#003737] transition-colors"
              title="Stop"
            >
              <span className="w-3 h-3 rounded-sm bg-white" />
            </button>
          ) : (
            <button
              onClick={fire}
              disabled={(!v.trim() && attachments.length === 0) || reading > 0}
              className="w-9 h-9 shrink-0 grid place-items-center rounded-full bg-[#005961] text-white disabled:opacity-40 hover:bg-[#003737] transition-colors"
              title={reading > 0 ? "Waiting for attachments…" : "Send"}
            >
              <ArrowUp className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

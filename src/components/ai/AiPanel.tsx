import { useEffect, useRef, useState } from "react";
import { ArrowUp, Sparkles, Check, Pencil, X, Compass, Wand2 } from "lucide-react";
import { Drawer, DrawerContent, DrawerHeader, DrawerTitle } from "@/components/ui/drawer";
import { useIsMobile } from "@/hooks/use-mobile";
import { useAi } from "./ai-store";
import { useSpira } from "@/lib/spira/store";
import { cn } from "@/lib/utils";
import type { AiAction, ChatMessage } from "@/lib/spira/types";
import { toast } from "sonner";

const GROW_STEPS = [
  { key: "G", title: "Goal", q: "What do you want to achieve, specifically? Describe the outcome." },
  { key: "R", title: "Reality", q: "Where are you right now in relation to this goal? What have you tried?" },
  { key: "O", title: "Options", q: "What strategies could move you forward? Brainstorm 3 paths." },
  { key: "W", title: "Will", q: "What's the next concrete target you'll commit to this week?" },
] as const;

const MIN_PANEL_WIDTH = 360;
const MAIN_CONTENT_MIN_WIDTH = 800;
const RESIZE_KEY = "spira:ai-coach-panel-width";

function maxPanelWidth() {
  if (typeof window === "undefined") return 520;
  return Math.max(MIN_PANEL_WIDTH, window.innerWidth - MAIN_CONTENT_MIN_WIDTH);
}

function clampPanelWidth(width: number) {
  return Math.max(MIN_PANEL_WIDTH, Math.min(maxPanelWidth(), width));
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
    const onResize = () => setWidth((current) => clampPanelWidth(current));
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(RESIZE_KEY, String(width));
  }, [width]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    setWide(isOpen && !isMobile && width >= window.innerWidth / 2);
  }, [isMobile, isOpen, setWide, width]);

  const startDrag = (e: React.PointerEvent) => {
    e.preventDefault();
    draggingRef.current = true;
    setIsDragging(true);
    handleRef.current?.setAttribute("data-dragging", "true");
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";

    const onMove = (ev: PointerEvent) => {
      if (!draggingRef.current) return;
      setWidth(clampPanelWidth(ev.clientX));
    };
    const onUp = () => {
      draggingRef.current = false;
      setIsDragging(false);
      handleRef.current?.removeAttribute("data-dragging");
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
  };

  const Body = <Conversation />;

  if (isMobile) {
    return (
      <Drawer open={isOpen} onOpenChange={(o) => !o && close()}>
        <DrawerContent className="h-[88vh] flex flex-col px-0 border-0 bg-[#006d67] text-white">
          <DrawerHeader className="px-5 pb-3 border-b border-white/15">
            <DrawerTitle className="flex items-baseline gap-2 text-white">
              <span className="text-[32px] font-extrabold leading-none">spira</span>
              <span className="text-[20px] font-normal leading-none">ai coach</span>
            </DrawerTitle>
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
      <div className="flex h-16 shrink-0 items-center justify-between gap-3 border-b border-white/15 px-5">
        <div className="flex min-w-0 items-baseline gap-2">
          <span className="text-[32px] font-extrabold leading-none text-white">spira</span>
          <span className="truncate pt-1 text-[20px] font-normal leading-none text-white">ai coach</span>
        </div>
        <button
          onClick={close}
          className="grid h-9 w-9 shrink-0 place-items-center rounded-md text-white/85 transition-colors hover:bg-white/15 hover:text-white"
          aria-label="Close spira ai coach"
          title="Close"
        >
          <X className="h-4.5 w-4.5" />
        </button>
      </div>
      <div className="min-h-0 flex-1 flex flex-col">{Body}</div>
    </aside>
  );
}

function Conversation() {
  const { mode, setMode, context } = useAi();
  const goal = useSpira((s) => s.goals.find((g) => g.id === context.goalId));
  const chat = useSpira((s) => s.chat);
  const addChatMessage = useSpira((s) => s.addChatMessage);
  const resolveAction = useSpira((s) => s.resolveAction);
  const addTarget = useSpira((s) => s.addTarget);
  const [input, setInput] = useState("");
  const [growIdx, setGrowIdx] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: 99999, behavior: "smooth" });
  }, [chat.length]);

  const send = () => {
    const text = input.trim();
    if (!text) return;
    addChatMessage({ role: "user", content: text });
    setInput("");

    setTimeout(() => {
      if (mode === "coaching") {
        const idx = Math.min(growIdx + 1, GROW_STEPS.length - 1);
        const step = GROW_STEPS[idx];
        addChatMessage({
          role: "assistant",
          content: `**${step.title}** — ${step.q}`,
        });
        setGrowIdx(idx);
      } else {
        // Mock: propose an action
        const action: AiAction = {
          id: Math.random().toString(36).slice(2, 9),
          goalId: goal?.id,
          title: "Add a numeric target: 5 outreach messages this week",
          description:
            "Create a numeric target tracking outreach messages, with weekly cadence.",
          reasoning:
            "You mentioned wanting to move forward but feeling stuck — a small, concrete weekly target lowers activation energy and creates feedback.",
          status: "pending",
        };
        addChatMessage({
          role: "assistant",
          content:
            "Here's a small step that could compound. Approve and I'll add it as a target on this goal.",
          action,
        });
      }
    }, 400);
  };

  const onApprove = (msg: ChatMessage) => {
    if (!msg.action) return;
    if (goal) {
      addTarget(goal.id, {
        type: "numeric",
        title: msg.action.title.replace(/^Add a numeric target: /, ""),
        current: 0,
        total: 5,
      } as any);
      toast.success("Target added");
    } else {
      toast("Open a goal first to attach this target.");
    }
    resolveAction(msg.id, "approved");
  };

  const startCoaching = () => {
    setMode("coaching");
    setGrowIdx(0);
    addChatMessage({
      role: "assistant",
      content: `**${GROW_STEPS[0].title}** — ${GROW_STEPS[0].q}`,
    });
  };

  return (
    <>
      {/* Context + mode bar */}
      <div className="px-4 sm:px-5 py-2.5 border-b border-white/15 flex items-center gap-2 text-xs text-white/75">
        <span>Context:</span>
        <span className="truncate font-medium text-white">
          {goal ? goal.title : "Global — all goals"}
        </span>
        <span className="ml-auto flex items-center gap-1 shrink-0">
          <ModeChip
            active={mode === "assistant"}
            onClick={() => setMode("assistant")}
            icon={<Wand2 className="h-3 w-3" />}
            label="Assistant"
          />
          <ModeChip
            active={mode === "coaching"}
            onClick={startCoaching}
            icon={<Compass className="h-3 w-3" />}
            label="Coaching"
          />
        </span>
      </div>

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 sm:px-5 py-4 space-y-4">
        {chat.length === 0 && (
          <div className="text-center pt-8 space-y-2">
            <div className="inline-flex h-12 w-12 rounded-full bg-white/10 border border-white/20 items-center justify-center text-white">
              <Sparkles className="h-5 w-5" />
            </div>
            <p className="text-sm text-white/75 max-w-xs mx-auto">
              Ask anything. I can help define goals, plan, or coach you through GROW. I'll propose
              actions — you approve them.
            </p>
          </div>
        )}
        {chat.map((m) => (
          <MessageBubble key={m.id} msg={m} onApprove={onApprove} onReject={(id) => resolveAction(id, "rejected")} />
        ))}
      </div>

      <div className="border-t border-white/15 p-3 sm:p-4">
        <div className="flex items-end gap-2 rounded-md border border-white/35 bg-white px-3.5 py-2 shadow-sm transition-colors focus-within:border-white focus-within:ring-[3px] focus-within:ring-white/20">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                send();
              }
            }}
            placeholder={
              mode === "coaching" ? "Answer the question…" : "Ask, plan, or request an action…"
            }
            rows={1}
            className="flex-1 bg-transparent resize-none outline-none text-base text-[#083f3a] placeholder:text-[#083f3a]/50 max-h-32"
          />
          <button
            onClick={send}
            disabled={!input.trim()}
            className="h-8 w-8 grid place-items-center rounded-lg bg-[#006d67] text-white disabled:opacity-40 transition-opacity hover:bg-[#005b56]"
          >
            <ArrowUp className="h-4 w-4" />
          </button>
        </div>
      </div>
    </>
  );
}

function ModeChip({
  active,
  onClick,
  icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1 px-2 py-1 rounded-md border text-[11px] transition-colors",
        active
          ? "bg-white border-white text-[#006d67]"
          : "bg-transparent border-white/25 text-white/75 hover:bg-white/10 hover:text-white",
      )}
    >
      {icon}
      {label}
    </button>
  );
}

function MessageBubble({
  msg,
  onApprove,
  onReject,
}: {
  msg: ChatMessage;
  onApprove: (m: ChatMessage) => void;
  onReject: (id: string) => void;
}) {
  if (msg.role === "user") {
    return (
      <div className="flex justify-end">
        <div className="max-w-[85%] rounded-2xl rounded-br-sm bg-white border border-white px-3.5 py-2 text-sm leading-relaxed text-[#083f3a]">
          {msg.content}
        </div>
      </div>
    );
  }
  return (
    <div className="space-y-2">
      <div className="max-w-[90%] text-sm leading-relaxed text-white/90 whitespace-pre-wrap">
        {renderMarkdownLite(msg.content)}
      </div>
      {msg.action && <ActionCard action={msg.action} onApprove={() => onApprove(msg)} onReject={() => onReject(msg.id)} />}
    </div>
  );
}

function renderMarkdownLite(text: string) {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((p, i) =>
    p.startsWith("**") && p.endsWith("**") ? (
      <strong key={i} className="font-display text-white">
        {p.slice(2, -2)}
      </strong>
    ) : (
      <span key={i}>{p}</span>
    ),
  );
}

function ActionCard({
  action,
  onApprove,
  onReject,
}: {
  action: AiAction;
  onApprove: () => void;
  onReject: () => void;
}) {
  const settled = action.status !== "pending";
  return (
    <div className="max-w-full rounded-md border border-white/30 bg-white p-3.5 text-[#083f3a] shadow-sm">
      <div className="flex items-center gap-2 text-[10px] uppercase tracking-wider text-[#006d67]">
        <Sparkles className="h-3 w-3" /> Proposed action
      </div>
      <div className="mt-1.5 font-display text-base">{action.title}</div>
      <p className="mt-1 text-sm text-[#083f3a]/65">{action.description}</p>
      <details className="mt-2 text-xs text-[#083f3a]/65">
        <summary className="cursor-pointer hover:text-[#083f3a]">Why this</summary>
        <p className="mt-1 leading-relaxed">{action.reasoning}</p>
      </details>
      {settled ? (
        <div
          className={cn(
            "mt-3 inline-flex items-center gap-1.5 text-xs px-2 py-1 rounded-md",
            action.status === "approved"
              ? "bg-[#006d67]/10 text-[#006d67]"
              : "bg-muted text-muted-foreground",
          )}
        >
          {action.status === "approved" ? (
            <>
              <Check className="h-3 w-3" /> Approved
            </>
          ) : (
            <>
              <X className="h-3 w-3" /> Rejected
            </>
          )}
        </div>
      ) : (
        <div className="mt-3 flex items-center gap-2">
          <button
            onClick={onApprove}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-[#006d67] text-white text-xs font-medium hover:bg-[#005b56]"
          >
            <Check className="h-3.5 w-3.5" /> Approve
          </button>
          <button className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md border hairline-strong text-xs hover:bg-accent">
            <Pencil className="h-3.5 w-3.5" /> Edit
          </button>
          <button
            onClick={onReject}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs text-muted-foreground hover:text-destructive ml-auto"
          >
            <X className="h-3.5 w-3.5" /> Reject
          </button>
        </div>
      )}
    </div>
  );
}

import { useEffect, useRef, useState } from "react";
import { Send, Sparkles, Check, Pencil, X, Compass, Wand2 } from "lucide-react";
import { Drawer, DrawerContent, DrawerHeader, DrawerTitle } from "@/components/ui/drawer";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
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

export function AiPanel() {
  const isOpen = useAi((s) => s.isOpen);
  const close = useAi((s) => s.close);
  const isMobile = useIsMobile();

  const Body = <Conversation />;

  if (isMobile) {
    return (
      <Drawer open={isOpen} onOpenChange={(o) => !o && close()}>
        <DrawerContent className="h-[88vh] flex flex-col px-0 bg-surface">
          <DrawerHeader className="px-5 pb-3 border-b hairline">
            <DrawerTitle className="font-display text-2xl flex items-center gap-2">
              <Sparkles className="h-5 w-5 text-primary" />
              Spira Assistant
            </DrawerTitle>
          </DrawerHeader>
          <div className="flex-1 min-h-0 flex flex-col">{Body}</div>
        </DrawerContent>
      </Drawer>
    );
  }

  return (
    <Sheet open={isOpen} onOpenChange={(o) => !o && close()}>
      <SheetContent
        side="right"
        className="w-full sm:max-w-md md:max-w-lg p-0 flex flex-col bg-surface border-l hairline"
      >
        <SheetHeader className="px-5 py-4 border-b hairline">
          <SheetTitle className="font-display text-2xl flex items-center gap-2">
            <Sparkles className="h-5 w-5 text-primary" />
            Spira Assistant
          </SheetTitle>
        </SheetHeader>
        <div className="flex-1 min-h-0 flex flex-col">{Body}</div>
      </SheetContent>
    </Sheet>
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
      <div className="px-4 sm:px-5 py-2.5 border-b hairline flex items-center gap-2 text-xs">
        <span className="text-muted-foreground">Context:</span>
        <span className="truncate font-medium">
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
            <div className="inline-flex h-12 w-12 rounded-full bg-primary-soft border border-primary/30 items-center justify-center text-primary">
              <Sparkles className="h-5 w-5" />
            </div>
            <p className="text-sm text-muted-foreground max-w-xs mx-auto">
              Ask anything. I can help define goals, plan, or coach you through GROW. I'll propose
              actions — you approve them.
            </p>
          </div>
        )}
        {chat.map((m) => (
          <MessageBubble key={m.id} msg={m} onApprove={onApprove} onReject={(id) => resolveAction(id, "rejected")} />
        ))}
      </div>

      <div className="border-t hairline p-3 sm:p-4">
        <div className="flex items-end gap-2 surface-sunken rounded-xl px-3 py-2">
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
            className="flex-1 bg-transparent resize-none outline-none text-sm py-1.5 max-h-32"
          />
          <button
            onClick={send}
            disabled={!input.trim()}
            className="h-8 w-8 grid place-items-center rounded-lg bg-primary text-primary-foreground disabled:opacity-40 transition-opacity"
          >
            <Send className="h-4 w-4" />
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
          ? "bg-primary/15 border-primary/40 text-primary"
          : "bg-surface border-border text-muted-foreground hover:text-foreground",
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
        <div className="max-w-[85%] rounded-2xl rounded-br-sm bg-primary/15 border border-primary/20 px-3.5 py-2 text-sm leading-relaxed">
          {msg.content}
        </div>
      </div>
    );
  }
  return (
    <div className="space-y-2">
      <div className="max-w-[90%] text-sm leading-relaxed text-foreground/90 whitespace-pre-wrap">
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
      <strong key={i} className="font-display text-foreground">
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
    <div className="surface-raised p-3.5 max-w-full">
      <div className="flex items-center gap-2 text-[10px] uppercase tracking-wider text-primary">
        <Sparkles className="h-3 w-3" /> Proposed action
      </div>
      <div className="mt-1.5 font-display text-base">{action.title}</div>
      <p className="mt-1 text-sm text-muted-foreground">{action.description}</p>
      <details className="mt-2 text-xs text-muted-foreground">
        <summary className="cursor-pointer hover:text-foreground">Why this</summary>
        <p className="mt-1 leading-relaxed">{action.reasoning}</p>
      </details>
      {settled ? (
        <div
          className={cn(
            "mt-3 inline-flex items-center gap-1.5 text-xs px-2 py-1 rounded-md",
            action.status === "approved"
              ? "bg-primary/15 text-primary"
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
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-primary text-primary-foreground text-xs font-medium hover:bg-primary/90"
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

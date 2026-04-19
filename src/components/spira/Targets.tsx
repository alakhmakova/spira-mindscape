import { useState, useRef, useEffect } from "react";
import { Check, Minus, Plus, Trash2, X } from "lucide-react";
import type { Goal, Target } from "@/lib/spira/types";
import { useSpira } from "@/lib/spira/store";
import { targetProgress } from "@/lib/spira/progress";
import { ProgressBar } from "./ProgressBar";
import { DeadlinePopover } from "./DeadlinePopover";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { Sheet, SheetContent } from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { useIsMobile } from "@/hooks/use-mobile";
import { cn } from "@/lib/utils";

export function TargetsList({ goal }: { goal: Goal }) {
  const { updateTarget, removeTarget } = useSpira();

  return (
    <div className="space-y-3">
      {goal.targets.length === 0 && (
        <p className="text-sm text-muted-foreground italic">
          Targets are how you execute. Add a numeric, binary, or checklist target.
        </p>
      )}
      <ul className="space-y-3">
        {goal.targets.map((t) => (
          <TargetRow
            key={t.id}
            target={t}
            onUpdate={(patch) => updateTarget(goal.id, t.id, patch)}
            onRemove={() => removeTarget(goal.id, t.id)}
          />
        ))}
      </ul>
    </div>
  );
}

function TargetRow({
  target,
  onUpdate,
  onRemove,
}: {
  target: Target;
  onUpdate: (patch: Partial<Target>) => void;
  onRemove: () => void;
}) {
  const progress = targetProgress(target);

  return (
    <li className="surface-card p-4 sm:p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0 space-y-2">
          {/* Top row: deadline (replaces type label) */}
          <DeadlinePopover
            iso={target.deadline}
            onChange={(next) => onUpdate({ deadline: next } as Partial<Target>)}
          />
          <input
            value={target.title}
            onChange={(e) => onUpdate({ title: e.target.value } as Partial<Target>)}
            className="w-full bg-transparent outline-none text-base font-semibold text-foreground"
          />
        </div>
        <button
          onClick={onRemove}
          className="shrink-0 text-muted-foreground hover:text-destructive p-2 -m-1 rounded-md hover:bg-secondary"
          aria-label="Delete target"
        >
          <Trash2 className="h-4 w-4" />
        </button>
      </div>

      {target.type === "numeric" && (
        <NumericBody target={target} onUpdate={onUpdate} progress={progress} />
      )}

      {target.type === "binary" && (
        <button
          onClick={() => onUpdate({ done: !target.done } as Partial<Target>)}
          className={cn(
            "mt-4 w-full flex items-center gap-3 px-4 h-12 rounded-md border-2 text-sm font-semibold transition-colors text-left",
            target.done
              ? "bg-primary-soft border-primary text-primary"
              : "bg-surface border-border-strong hover:border-primary",
          )}
        >
          <span
            className={cn(
              "h-5 w-5 rounded-sm border-2 grid place-items-center shrink-0 transition-colors",
              target.done ? "bg-primary border-primary" : "border-border-strong",
            )}
          >
            {target.done && (
              <Check className="h-3.5 w-3.5 text-primary-foreground" strokeWidth={3} />
            )}
          </span>
          <span className="flex-1">{target.done ? "Done" : "Mark done"}</span>
        </button>
      )}

      {target.type === "checklist" && (
        <>
          <ChecklistEditor
            items={target.items}
            onChange={(items) => onUpdate({ items } as Partial<Target>)}
          />
          <div className="mt-4 flex items-center gap-3">
            <ProgressBar value={progress} className="flex-1" />
            <span className="num text-xs text-muted-foreground font-semibold">
              {Math.round(progress * 100)}%
            </span>
          </div>
        </>
      )}
    </li>
  );
}

function NumericBody({
  target,
  onUpdate,
  progress,
}: {
  target: Extract<Target, { type: "numeric" }>;
  onUpdate: (patch: Partial<Target>) => void;
  progress: number;
}) {
  return (
    <div className="mt-4 space-y-2">
      {/* Inline-editable current / total / unit — centered above the bar */}
      <div className="flex items-center justify-center gap-1 num font-semibold tabular-nums text-sm">
        <InlineNumber
          value={target.current}
          min={0}
          onChange={(v) => onUpdate({ current: v } as Partial<Target>)}
          ariaLabel="Current value"
        />
        <span className="text-muted-foreground font-normal">/</span>
        <InlineNumber
          value={target.total}
          min={0}
          onChange={(v) => onUpdate({ total: v } as Partial<Target>)}
          ariaLabel="Total value"
          tone="muted"
        />
        <InlineText
          value={target.unit ?? ""}
          placeholder="unit"
          onChange={(v) => onUpdate({ unit: v || undefined } as Partial<Target>)}
          ariaLabel="Unit"
        />
      </div>
      {/* Single progress bar with ± controls; percentage sits inline before the + */}
      <div className="flex items-center gap-3">
        <button
          onClick={() => onUpdate({ current: Math.max(0, target.current - 1) } as Partial<Target>)}
          className="h-9 w-9 grid place-items-center rounded-md border-2 border-border hover:border-primary hover:text-primary"
          aria-label="Decrement"
        >
          <Minus className="h-4 w-4" />
        </button>
        <ProgressBar value={progress} className="flex-1" />
        <span className="num text-xs font-semibold tabular-nums text-foreground/80 min-w-[3ch] text-right">
          {Math.round(progress * 100)}%
        </span>
        <button
          onClick={() => onUpdate({ current: target.current + 1 } as Partial<Target>)}
          className="h-9 w-9 grid place-items-center rounded-md border-2 border-border hover:border-primary hover:text-primary"
          aria-label="Increment"
        >
          <Plus className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

function InlineNumber({
  value,
  onChange,
  min,
  ariaLabel,
  tone = "default",
}: {
  value: number;
  onChange: (v: number) => void;
  min?: number;
  ariaLabel: string;
  tone?: "default" | "muted";
}) {
  const ref = useRef<HTMLInputElement>(null);
  const [w, setW] = useState(`${String(value).length || 1}ch`);
  useEffect(() => {
    setW(`${Math.max(1, String(value).length)}ch`);
  }, [value]);
  return (
    <input
      ref={ref}
      type="text"
      inputMode="numeric"
      pattern="[0-9]*"
      value={value}
      aria-label={ariaLabel}
      onFocus={(e) => e.currentTarget.select()}
      onChange={(e) => {
        const raw = e.target.value.replace(/[^0-9]/g, "");
        const n = raw === "" ? 0 : parseInt(raw, 10);
        if (typeof min === "number" && n < min) return;
        onChange(n);
      }}
      style={{ width: w }}
      className={cn(
        "bg-transparent outline-none rounded-sm px-0.5 num tabular-nums text-center cursor-text border-b border-dashed border-border-strong/60 hover:border-primary hover:bg-primary-soft/40 focus:bg-primary-soft focus:border-primary focus:ring-2 focus:ring-primary/30 transition-colors",
        tone === "muted" && "text-muted-foreground font-normal",
      )}
    />
  );
}

function InlineText({
  value,
  onChange,
  placeholder,
  ariaLabel,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder: string;
  ariaLabel: string;
}) {
  const [w, setW] = useState("4ch");
  useEffect(() => {
    setW(`${Math.max(value.length || placeholder.length, 3)}ch`);
  }, [value, placeholder]);
  return (
    <input
      type="text"
      value={value}
      placeholder={placeholder}
      aria-label={ariaLabel}
      onFocus={(e) => e.currentTarget.select()}
      onChange={(e) => onChange(e.target.value)}
      style={{ width: w }}
      className="ml-1 bg-transparent outline-none rounded-sm px-0.5 text-muted-foreground font-normal cursor-text border-b border-dashed border-border-strong/60 placeholder:text-muted-foreground/50 hover:border-primary hover:bg-primary-soft/40 focus:bg-primary-soft focus:text-foreground focus:border-primary focus:ring-2 focus:ring-primary/30 transition-colors"
    />
  );
}

function ChecklistEditor({
  items,
  onChange,
}: {
  items: { id: string; text: string; done: boolean }[];
  onChange: (items: { id: string; text: string; done: boolean }[]) => void;
}) {
  const [draft, setDraft] = useState("");
  const uid = () => Math.random().toString(36).slice(2, 9);
  return (
    <div className="mt-4 space-y-1.5">
      {items.map((it) => (
        <div
          key={it.id}
          className={cn(
            "group flex items-center gap-3 px-2 py-1.5 rounded-md transition-colors",
            it.done ? "bg-primary-soft/40" : "hover:bg-secondary/60",
          )}
        >
          <button
            onClick={() =>
              onChange(items.map((i) => (i.id === it.id ? { ...i, done: !i.done } : i)))
            }
            className={cn(
              "h-4 w-4 rounded-sm border-2 grid place-items-center shrink-0 transition-colors",
              it.done ? "bg-primary border-primary" : "border-border-strong hover:border-primary",
            )}
          >
            {it.done && <Check className="h-3 w-3 text-primary-foreground" strokeWidth={3} />}
          </button>
          <input
            value={it.text}
            onChange={(e) =>
              onChange(items.map((i) => (i.id === it.id ? { ...i, text: e.target.value } : i)))
            }
            className={cn(
              "flex-1 bg-transparent outline-none text-sm",
              it.done && "line-through text-muted-foreground",
            )}
          />
          <button
            onClick={() => onChange(items.filter((i) => i.id !== it.id))}
            className="opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-destructive p-1"
          >
            <X className="h-3 w-3" />
          </button>
        </div>
      ))}
      <div className="flex items-center gap-3 px-2 py-1.5">
        <span className="h-4 w-4 rounded-sm border-2 border-dashed border-border-strong shrink-0" />
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && draft.trim()) {
              onChange([...items, { id: uid(), text: draft.trim(), done: false }]);
              setDraft("");
            }
          }}
          placeholder="Add subtask…"
          className="flex-1 bg-transparent outline-none text-sm placeholder:text-muted-foreground py-0.5"
        />
      </div>
    </div>
  );
}

export function NewTargetSheet({
  goalId,
  open,
  onOpenChange,
}: {
  goalId: string;
  open: boolean;
  onOpenChange: (o: boolean) => void;
}) {
  const isMobile = useIsMobile();
  const Body = <NewTargetForm goalId={goalId} onDone={() => onOpenChange(false)} />;

  if (isMobile) {
    return (
      <Drawer open={open} onOpenChange={onOpenChange}>
        <DrawerContent className="px-0 pb-6 max-h-[92vh] flex flex-col">{Body}</DrawerContent>
      </Drawer>
    );
  }
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="w-full sm:max-w-lg p-0 flex flex-col bg-surface border-l hairline"
      >
        {Body}
      </SheetContent>
    </Sheet>
  );
}

function NewTargetForm({ goalId, onDone }: { goalId: string; onDone: () => void }) {
  const addTarget = useSpira((s) => s.addTarget);
  const [type, setType] = useState<"numeric" | "binary" | "checklist">("numeric");
  const [title, setTitle] = useState("");
  const [total, setTotal] = useState(10);
  const [unit, setUnit] = useState("");
  const [deadline, setDeadline] = useState("");

  const submit = () => {
    if (!title.trim()) return;
    const base = {
      title: title.trim(),
      deadline: deadline ? new Date(deadline).toISOString() : undefined,
    };
    if (type === "numeric") {
      addTarget(goalId, {
        ...base,
        type: "numeric",
        current: 0,
        total,
        unit: unit || undefined,
      } as any);
    } else if (type === "binary") {
      addTarget(goalId, { ...base, type: "binary", done: false } as any);
    } else {
      addTarget(goalId, { ...base, type: "checklist", items: [] } as any);
    }
    onDone();
  };

  return (
    <>
      <div className="px-7 py-5 border-b hairline flex items-center justify-between sticky top-0 bg-surface z-10">
        <h2 className="font-sans font-bold text-lg">Add a target</h2>
        <button
          onClick={onDone}
          className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      <div className="px-7 py-6 space-y-6 overflow-y-auto flex-1">
        <div>
          <label className="text-sm font-semibold block mb-2">
            Type <span className="text-destructive">*</span>
          </label>
          <div className="space-y-2">
            {(
              [
                {
                  v: "numeric",
                  t: "Numeric",
                  d: "Track a number toward a target (e.g. 12 / 40 apps)",
                },
                { v: "binary", t: "Binary", d: "A single done / not-done outcome" },
                { v: "checklist", t: "Checklist", d: "Subtasks with optional deadlines" },
              ] as const
            ).map((opt) => (
              <button
                key={opt.v}
                onClick={() => setType(opt.v)}
                className={cn(
                  "w-full text-left px-4 py-3 rounded-md border-2 transition-colors flex items-start gap-3",
                  type === opt.v
                    ? "bg-primary-soft border-primary"
                    : "bg-surface border-border hover:border-border-strong",
                )}
              >
                <span
                  className={cn(
                    "mt-0.5 h-5 w-5 rounded-full border-2 grid place-items-center shrink-0",
                    type === opt.v ? "border-primary" : "border-border-strong",
                  )}
                >
                  {type === opt.v && <span className="h-2.5 w-2.5 rounded-full bg-primary" />}
                </span>
                <span>
                  <span className="block font-semibold text-sm text-foreground">{opt.t}</span>
                  <span className="block text-xs text-muted-foreground mt-0.5">{opt.d}</span>
                </span>
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="text-sm font-semibold block mb-1.5">
            Title <span className="text-destructive">*</span>
          </label>
          <Input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Outbound applications"
            className="h-11 bg-surface border-2 border-border focus-visible:border-primary"
            autoFocus
          />
        </div>
        {type === "numeric" && (
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-sm font-semibold block mb-1.5">Total</label>
              <Input
                type="number"
                value={total}
                onChange={(e) => setTotal(Number(e.target.value) || 0)}
                className="h-11 bg-surface border-2 border-border focus-visible:border-primary"
              />
            </div>
            <div>
              <label className="text-sm font-semibold block mb-1.5">Unit</label>
              <Input
                value={unit}
                onChange={(e) => setUnit(e.target.value)}
                placeholder="apps, km…"
                className="h-11 bg-surface border-2 border-border focus-visible:border-primary"
              />
            </div>
          </div>
        )}
        <div>
          <label className="text-sm font-semibold block mb-1.5">
            Deadline <span className="text-muted-foreground font-normal">(optional)</span>
          </label>
          <Input
            type="date"
            value={deadline}
            onChange={(e) => setDeadline(e.target.value)}
            className="h-11 bg-surface border-2 border-border focus-visible:border-primary"
          />
        </div>
      </div>

      <div className="px-7 py-4 border-t hairline flex items-center justify-end gap-3 bg-surface">
        <button onClick={onDone} className="link-action h-11 px-4 text-sm font-semibold">
          Cancel
        </button>
        <button
          onClick={submit}
          disabled={!title.trim()}
          className="h-11 px-5 rounded-md bg-primary text-primary-foreground font-semibold text-sm hover:bg-primary/90 disabled:opacity-40"
        >
          Add target
        </button>
      </div>
    </>
  );
}

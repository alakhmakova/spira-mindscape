import { useState } from "react";
import { Check, Minus, Plus, Trash2, X } from "lucide-react";
import type { Goal, Target } from "@/lib/spira/types";
import { useSpira } from "@/lib/spira/store";
import { targetProgress } from "@/lib/spira/progress";
import { ProgressBar } from "./ProgressBar";
import { DeadlineLabel } from "./DeadlineLabel";
import { Drawer, DrawerContent, DrawerHeader, DrawerTitle } from "@/components/ui/drawer";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { useIsMobile } from "@/hooks/use-mobile";
import { cn } from "@/lib/utils";

export function TargetsList({ goal }: { goal: Goal }) {
  const { updateTarget, removeTarget } = useSpira();

  return (
    <div className="space-y-2">
      {goal.targets.length === 0 && (
        <p className="text-sm text-muted-foreground italic">
          Targets are how you execute. Add a numeric, binary, or checklist target.
        </p>
      )}
      <ul className="space-y-2">
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
  const typeLabel =
    target.type === "numeric" ? "Numeric" : target.type === "binary" ? "Binary" : "Checklist";

  return (
    <li className="surface-sunken p-3 sm:p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 text-[10px] uppercase tracking-wider text-muted-foreground">
            <span>{typeLabel}</span>
            {target.deadline && <DeadlineLabel iso={target.deadline} />}
          </div>
          <input
            value={target.title}
            onChange={(e) => onUpdate({ title: e.target.value } as Partial<Target>)}
            className="mt-1 w-full bg-transparent outline-none text-base font-medium"
          />
        </div>
        <button
          onClick={onRemove}
          className="text-muted-foreground hover:text-destructive p-1"
          aria-label="Delete target"
        >
          <Trash2 className="h-4 w-4" />
        </button>
      </div>

      {target.type === "numeric" && (
        <div className="mt-3 flex items-center gap-3">
          <button
            onClick={() => onUpdate({ current: Math.max(0, target.current - 1) } as Partial<Target>)}
            className="h-8 w-8 grid place-items-center rounded-md border hairline-strong hover:bg-accent"
          >
            <Minus className="h-4 w-4" />
          </button>
          <div className="flex-1 flex items-center gap-3">
            <ProgressBar value={progress} className="flex-1" />
            <span className="num text-sm tabular-nums">
              {target.current}
              <span className="text-muted-foreground"> / {target.total}</span>
              {target.unit && <span className="text-muted-foreground ml-1">{target.unit}</span>}
            </span>
          </div>
          <button
            onClick={() => onUpdate({ current: target.current + 1 } as Partial<Target>)}
            className="h-8 w-8 grid place-items-center rounded-md border hairline-strong hover:bg-accent"
          >
            <Plus className="h-4 w-4" />
          </button>
        </div>
      )}

      {target.type === "binary" && (
        <button
          onClick={() => onUpdate({ done: !target.done } as Partial<Target>)}
          className={cn(
            "mt-3 inline-flex items-center gap-2 px-3 py-1.5 rounded-md border text-sm transition-colors",
            target.done
              ? "bg-primary/15 border-primary/40 text-primary"
              : "bg-surface border-border hover:border-border-strong",
          )}
        >
          <Check className="h-3.5 w-3.5" />
          {target.done ? "Done" : "Mark done"}
        </button>
      )}

      {target.type === "checklist" && (
        <ChecklistEditor
          items={target.items}
          onChange={(items) => onUpdate({ items } as Partial<Target>)}
        />
      )}

      {target.type !== "binary" && (
        <div className="mt-3 flex items-center justify-between">
          <ProgressBar value={progress} className="flex-1 mr-3" />
          <span className="num text-xs text-muted-foreground">{Math.round(progress * 100)}%</span>
        </div>
      )}
    </li>
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
    <div className="mt-3 space-y-1">
      {items.map((it) => (
        <div key={it.id} className="group flex items-center gap-2">
          <button
            onClick={() => onChange(items.map((i) => (i.id === it.id ? { ...i, done: !i.done } : i)))}
            className={cn(
              "h-4 w-4 rounded-sm border-2 grid place-items-center shrink-0",
              it.done ? "bg-primary border-primary" : "border-border-strong",
            )}
          >
            {it.done && <Check className="h-2.5 w-2.5 text-primary-foreground" />}
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
      <div className="flex items-center gap-2">
        <span className="h-4 w-4 rounded-sm border-2 border-dashed border-border" />
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
          className="flex-1 bg-transparent outline-none text-sm placeholder:text-muted-foreground/60 py-1"
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
  const Body = (
    <NewTargetForm goalId={goalId} onDone={() => onOpenChange(false)} />
  );

  if (isMobile) {
    return (
      <Drawer open={open} onOpenChange={onOpenChange}>
        <DrawerContent className="px-4 pb-6">
          <DrawerHeader className="px-0">
            <DrawerTitle className="font-display text-2xl">New target</DrawerTitle>
          </DrawerHeader>
          {Body}
        </DrawerContent>
      </Drawer>
    );
  }
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full sm:max-w-md">
        <SheetHeader>
          <SheetTitle className="font-display text-2xl">New target</SheetTitle>
        </SheetHeader>
        <div className="mt-4">{Body}</div>
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
      addTarget(goalId, { ...base, type: "numeric", current: 0, total, unit: unit || undefined } as any);
    } else if (type === "binary") {
      addTarget(goalId, { ...base, type: "binary", done: false } as any);
    } else {
      addTarget(goalId, { ...base, type: "checklist", items: [] } as any);
    }
    onDone();
  };

  return (
    <div className="space-y-5">
      <div>
        <label className="text-xs uppercase tracking-wider text-muted-foreground">Type</label>
        <div className="mt-2 grid grid-cols-3 gap-2">
          {(["numeric", "binary", "checklist"] as const).map((t) => (
            <button
              key={t}
              onClick={() => setType(t)}
              className={cn(
                "py-2.5 rounded-md border text-sm capitalize transition-colors",
                type === t
                  ? "bg-primary/15 border-primary/40 text-primary"
                  : "bg-surface border-border hover:border-border-strong",
              )}
            >
              {t}
            </button>
          ))}
        </div>
      </div>
      <div>
        <label className="text-xs uppercase tracking-wider text-muted-foreground">Title</label>
        <Input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="e.g. Outbound applications"
          className="mt-1.5 h-11 bg-surface"
          autoFocus
        />
      </div>
      {type === "numeric" && (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="text-xs uppercase tracking-wider text-muted-foreground">Total</label>
            <Input
              type="number"
              value={total}
              onChange={(e) => setTotal(Number(e.target.value) || 0)}
              className="mt-1.5 h-11 bg-surface"
            />
          </div>
          <div>
            <label className="text-xs uppercase tracking-wider text-muted-foreground">Unit</label>
            <Input
              value={unit}
              onChange={(e) => setUnit(e.target.value)}
              placeholder="apps, km…"
              className="mt-1.5 h-11 bg-surface"
            />
          </div>
        </div>
      )}
      <div>
        <label className="text-xs uppercase tracking-wider text-muted-foreground">
          Deadline (optional)
        </label>
        <Input
          type="date"
          value={deadline}
          onChange={(e) => setDeadline(e.target.value)}
          className="mt-1.5 h-11 bg-surface"
        />
      </div>
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="ghost" onClick={onDone}>
          Cancel
        </Button>
        <Button onClick={submit} disabled={!title.trim()}>
          Add target
        </Button>
      </div>
    </div>
  );
}

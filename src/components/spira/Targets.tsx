import { useState, useRef, useEffect } from "react";
import { Check, Minus, Plus, Trash2, X } from "lucide-react";
import type { Goal, Target } from "@/lib/spira/types";
import { useSpira } from "@/lib/spira/store";
import { targetProgress } from "@/lib/spira/progress";
import { ProgressBar } from "./ProgressBar";
import { DeadlinePopover } from "./DeadlinePopover";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { Sheet, SheetContent } from "@/components/ui/sheet";
import { ResizableSheet } from "@/components/spira/Resources";
import { Input } from "@/components/ui/input";
import { useIsMobile } from "@/hooks/use-mobile";
import { cn } from "@/lib/utils";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem } from "@/components/ui/dropdown-menu";
import { InlineText } from "@/components/spira/Inline";
import { ConfirmDialog } from "@/components/spira/ConfirmDialog";

export function TargetsList({ goal }: { goal: Goal }) {
  const { updateTarget, removeTarget } = useSpira();
  const [confirmTarget, setConfirmTarget] = useState<Target | null>(null);
  
  useEffect(() => {
    if (typeof window === "undefined") return;
    const handleHash = () => {
      const hash = window.location.hash;
      if (!hash) return;
      if (hash.startsWith("#target-")) {
        const id = hash.replace("#target-", "");
        window.setTimeout(() => {
          let el = document.getElementById(`target-desktop-${id}`);
          if (!el || el.offsetParent === null) {
            el = document.getElementById(`target-mobile-${id}`);
          }
          if (el) {
            const yOffset = -112;
            const y = el.getBoundingClientRect().top + window.scrollY + yOffset;
            window.scrollTo({ top: y, behavior: "smooth" });
          }
        }, 10);
      }
    };
    handleHash();
    window.addEventListener("hashchange", handleHash);
    return () => window.removeEventListener("hashchange", handleHash);
  }, [goal.targets]);

  return (
    <div className="space-y-3">
      {goal.targets.length === 0 && (
        <p className="text-sm text-muted-foreground italic px-1">
          Targets are how you execute. Add a numeric, binary, or checklist target.
        </p>
      )}
      <ul className="spira-target-mobile-list space-y-3">
        {goal.targets.map((t) => (
          <TargetRow
            key={t.id}
            target={t}
            onUpdate={(patch) => updateTarget(goal.id, t.id, patch)}
            onRemove={() => setConfirmTarget(t)}
          />
        ))}
      </ul>
      {goal.targets.length > 0 && <DesktopTargetsTable goal={goal} />}
      <TargetDeleteConfirm
        target={confirmTarget}
        open={!!confirmTarget}
        onOpenChange={(open) => !open && setConfirmTarget(null)}
        onConfirm={() => {
          if (!confirmTarget) return;
          removeTarget(goal.id, confirmTarget.id);
          setConfirmTarget(null);
        }}
      />
    </div>
  );
}

function DesktopTargetsTable({ goal }: { goal: Goal }) {
  const { updateTarget, removeTarget } = useSpira();
  const [sortField, setSortField] = useState<"title" | "deadline" | "progress">("deadline");
  const [sortDesc, setSortDesc] = useState(false);
  const [editingTasksFor, setEditingTasksFor] = useState<string | null>(null);
  const [editingNumericFor, setEditingNumericFor] = useState<string | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<Target | null>(null);



  const toggleSort = (field: "title" | "deadline" | "progress") => {
    if (sortField === field) setSortDesc(!sortDesc);
    else {
      setSortField(field);
      setSortDesc(false);
    }
  };

  const sortedTargets = [...goal.targets].sort((a, b) => {
    let cmp = 0;
    if (sortField === "title") {
      cmp = a.title.localeCompare(b.title);
    } else if (sortField === "deadline") {
      const ad = a.deadline ? new Date(a.deadline).getTime() : Infinity;
      const bd = b.deadline ? new Date(b.deadline).getTime() : Infinity;
      cmp = ad - bd;
    } else if (sortField === "progress") {
      cmp = targetProgress(a) - targetProgress(b);
    }
    return sortDesc ? -cmp : cmp;
  });

  useEffect(() => {
    if (typeof window === "undefined") return;
    const handleHash = () => {
      const hash = window.location.hash;
      if (!hash) return;
      if (hash.startsWith("#task-")) {
        const taskId = hash.replace("#task-", "");
        const target = goal.targets.find((t) => t.type === "checklist" && t.items.some((item) => item.id === taskId));
        if (!target) return;
        setEditingTasksFor(target.id);
        window.setTimeout(() => document.getElementById(hash.slice(1))?.scrollIntoView({ behavior: "smooth", block: "center" }), 50);
      }
    };
    handleHash();
    window.addEventListener("hashchange", handleHash);
    return () => window.removeEventListener("hashchange", handleHash);
  }, [goal.targets]);

  const SortIcon = ({ field }: { field: string }) => {
    const active = sortField === field;
    return (
      <span className={cn("inline-flex flex-col items-center justify-center gap-[3px] ml-1.5", !active && "opacity-30 group-hover:opacity-60 transition-opacity")}>
        <svg width="8" height="5" viewBox="0 0 8 5" className={cn(active && !sortDesc ? "opacity-100" : "opacity-50")}>
          <path d="M4 0L8 5H0L4 0Z" fill="currentColor" />
        </svg>
        <svg width="8" height="5" viewBox="0 0 8 5" className={cn(active && sortDesc ? "opacity-100" : "opacity-50")}>
          <path d="M4 5L0 0H8L4 5Z" fill="currentColor" />
        </svg>
      </span>
    );
  };

  return (
    <div className="spira-target-desktop-table">
      <Table>
        <TableHeader className="bg-muted">
          <TableRow className="border-0 border-b">
            <TableHead className="cursor-pointer hover:text-foreground w-[45%] pl-6" onClick={() => toggleSort("title")}>
              <div className="flex items-center">Target Name <SortIcon field="title" /></div>
            </TableHead>
            <TableHead className="cursor-pointer hover:text-foreground w-[15%]" onClick={() => toggleSort("deadline")}>
              <div className="flex items-center">Deadline <SortIcon field="deadline" /></div>
            </TableHead>
            <TableHead className="w-[15%]">Status</TableHead>
            <TableHead className="cursor-pointer hover:text-foreground w-[15%]" onClick={() => toggleSort("progress")}>
              <div className="flex items-center">Progress <SortIcon field="progress" /></div>
            </TableHead>
            <TableHead className="w-[10%] text-right pr-6">Delete</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sortedTargets.map((t) => {
            const progress = targetProgress(t);
            return (
              <TableRow 
                key={t.id} 
                id={`target-desktop-${t.id}`} 
                className="group/row scroll-mt-24 cursor-pointer hover:bg-muted/50 transition-colors"
                onClick={(e) => {
                  const targetEl = e.target as HTMLElement;
                  if (targetEl.tagName === 'INPUT' || targetEl.closest('button')) return;
                  if (t.type === "numeric") setEditingNumericFor(t.id);
                  if (t.type === "checklist") setEditingTasksFor(t.id);
                }}
              >
                <TableCell className="pl-6">
                  <InlineText
                    value={t.title}
                    onChange={(title) => updateTarget(goal.id, t.id, { title })}
                    placeholder="Untitled target"
                    ariaLabel="Edit target title"
                    className="block w-full font-medium text-sm text-foreground"
                  />
                </TableCell>
                <TableCell>
                  <DeadlinePopover
                    iso={t.deadline}
                    variant="text"
                    side="top"
                    hideChevron
                    hideDaysLeft
                    onChange={(next) => updateTarget(goal.id, t.id, { deadline: next })}
                  />
                </TableCell>
                <TableCell>
                  {t.type === "binary" && (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <button className="flex items-center gap-2 group h-8">
                          <div className={cn("h-2 w-2 rounded-full shrink-0", t.done ? "bg-success" : "bg-muted-foreground/40")}></div>
                          <span className="text-sm text-foreground group-hover:text-primary transition-colors">
                            {t.done ? "Done" : "Not done"}
                          </span>
                        </button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="start" className="min-w-[120px]">
                        <DropdownMenuItem onClick={() => updateTarget(goal.id, t.id, { done: false })} className="text-sm">
                          Not done
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => updateTarget(goal.id, t.id, { done: true })} className="text-sm">
                          Done
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  )}
                  {t.type === "numeric" && (
                    <button
                      onClick={() => setEditingNumericFor(t.id)}
                      className="flex items-center gap-2 group h-8"
                    >
                       <div className={cn("h-2 w-2 rounded-full shrink-0", progress >= 1 ? "bg-success" : "bg-[#ea580c]")}></div>
                      <span className="text-sm text-foreground group-hover:text-primary transition-colors">{progress >= 1 ? "Complete" : "Update"}</span>
                    </button>
                  )}
                  {t.type === "checklist" && (
                    <button
                      onClick={() => setEditingTasksFor(t.id)}
                      className="flex items-center gap-2 group h-8"
                    >
                       <div className={cn("h-2 w-2 rounded-full shrink-0", progress >= 1 ? "bg-success" : "bg-[#ea580c]")}></div>
                      <span className="text-sm text-foreground group-hover:text-primary transition-colors">{progress >= 1 ? "Complete" : "Tasks"}</span>
                    </button>
                  )}
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-3">
                    <ProgressBar value={progress} className="w-full max-w-[80px]" />
                    <span className="text-xs font-semibold num tabular-nums text-foreground/80 min-w-[3ch] text-right">
                      {Math.round(progress * 100)}%
                    </span>
                  </div>
                </TableCell>
                <TableCell className="text-right pr-6">
                  <button
                    onClick={() => setConfirmTarget(t)}
                    className="text-foreground opacity-100 hover:text-destructive p-1.5 rounded-md hover:bg-secondary transition-colors inline-flex"
                    title="Delete target"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
      <TargetDeleteConfirm
        target={confirmTarget}
        open={!!confirmTarget}
        onOpenChange={(open) => !open && setConfirmTarget(null)}
        onConfirm={() => {
          if (!confirmTarget) return;
          removeTarget(goal.id, confirmTarget.id);
          setConfirmTarget(null);
        }}
      />
      
      {/* Numeric Updates Sheet */}
      <Sheet open={!!editingNumericFor} onOpenChange={(open) => !open && setEditingNumericFor(null)}>
        <SheetContent side="right" className="w-full sm:max-w-md p-0 flex flex-col bg-surface border-l hairline">
          {editingNumericFor && (
            <div className="flex-1 flex flex-col overflow-hidden">
              <div className="px-6 pt-5 pb-2 flex items-center justify-between bg-surface z-10 sticky top-0">
                <h3 className="font-bold">Update Progress</h3>
                <button
                  onClick={() => setEditingNumericFor(null)}
                  className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              <div className="flex-1 px-6 pb-6 pt-0 overflow-y-auto">
                {(() => {
                  const target = goal.targets.find(t => t.id === editingNumericFor);
                  if (!target || target.type !== "numeric") return null;
                  return (
                    <div className="pt-2">
                      <NumericBody
                        target={target}
                        onUpdate={(patch) => updateTarget(goal.id, target.id, patch)}
                        progress={targetProgress(target)}
                      />
                    </div>
                  );
                })()}
              </div>
              <div className="p-4 flex items-center justify-end gap-2 bg-surface">
                 <button onClick={() => setEditingNumericFor(null)} className="h-11 px-5 rounded-md border-2 border-border text-foreground font-semibold text-sm hover:bg-secondary transition-colors">Cancel</button>
                 <button onClick={() => setEditingNumericFor(null)} className="h-11 px-5 rounded-md bg-primary text-primary-foreground font-semibold text-sm hover:bg-primary/90 transition-colors">Save</button>
              </div>
            </div>
          )}
        </SheetContent>
      </Sheet>

      {/* Checklist Tasks Sheet */}
      <TasksResizableSheet
        open={!!editingTasksFor}
        onClose={() => setEditingTasksFor(null)}
        items={editingTasksFor ? (goal.targets.find(t => t.id === editingTasksFor)?.type === "checklist" ? (goal.targets.find(t => t.id === editingTasksFor) as Extract<Target, {type: "checklist"}>).items : []) : []}
        title={editingTasksFor ? (goal.targets.find(t => t.id === editingTasksFor)?.title ?? "Tasks") : "Tasks"}
        onChange={(items) => editingTasksFor && updateTarget(goal.id, editingTasksFor, { items })}
      />
    </div>
  );
}

function TargetDeleteConfirm({
  target,
  open,
  onOpenChange,
  onConfirm,
}: {
  target: Target | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
}) {
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={onOpenChange}
      title="Delete this target?"
      description={`Are you sure you want to permanently delete "${target?.title ?? "this target"}"? Progress and checklist tasks inside it will be removed. You can't undo this.`}
      confirmLabel="Yes, delete"
      cancelLabel="No, go back"
      onConfirm={onConfirm}
    />
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
    <li id={`target-mobile-${target.id}`} className="surface-card scroll-mt-24 p-4 sm:p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0 space-y-2">
          {/* Top row: deadline (replaces type label) */}
          <DeadlinePopover
            iso={target.deadline}
            onChange={(next) => onUpdate({ deadline: next } as Partial<Target>)}
          />
          <InlineText
            value={target.title}
            onChange={(title) => onUpdate({ title } as Partial<Target>)}
            ariaLabel="Edit target title"
            className="block w-full text-base font-semibold text-foreground"
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
            "mt-4 flex items-stretch overflow-hidden rounded-md border transition-colors min-h-[44px] w-full",
            target.done ? "border-primary" : "border-border hover:border-primary/50",
          )}
        >
          <div
            className={cn(
              "w-12 shrink-0 flex items-center justify-center border-r transition-colors",
              target.done ? "bg-primary-soft border-primary" : "bg-surface border-border hover:bg-secondary/50",
            )}
          >
            <div
              className={cn(
                "h-4 w-4 rounded-sm border-2 grid place-items-center transition-colors",
                target.done ? "bg-primary border-primary" : "border-border-strong",
              )}
            >
              {target.done && (
                <Check className="h-3 w-3 text-primary-foreground" strokeWidth={3} />
              )}
            </div>
          </div>
          <div className="flex-1 flex items-center px-3 bg-surface">
            <span className={cn(
              "text-sm",
              target.done ? "line-through text-muted-foreground" : "text-foreground font-medium",
            )}>
              {target.done ? "Done" : "Mark done"}
            </span>
          </div>
        </button>
      )}

      {target.type === "checklist" && (
        <>
          <ChecklistEditor
            items={target.items}
            onChange={(items) => onUpdate({ items } as Partial<Target>)}
            compact
            hideCountdown
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
  const updateCurrent = (next: number) => {
    const inferredStart =
      target.start === undefined && next > target.total
        ? target.current === 0
          ? next
          : 0
        : target.start;

    onUpdate({ ...(inferredStart !== undefined ? { start: inferredStart } : {}), current: next } as Partial<Target>);
  };

  const updateTotal = (next: number) => {
    onUpdate({ ...(target.start === undefined && target.current > next ? { start: target.current } : {}), total: next } as Partial<Target>);
  };

  return (
    <div className="mt-4 space-y-2">
      {/* Inline-editable current / total / unit — centered above the bar */}
      <div className="flex items-center justify-center gap-1 num font-semibold tabular-nums text-sm text-foreground">
        <InlineEditable
          value={String(target.current)}
          numeric
          min={0}
          onChange={(v) => updateCurrent(parseInt(v, 10))}
          ariaLabel="Current value"
        />
        <span>/</span>
        <InlineEditable
          value={String(target.total)}
          numeric
          min={0}
          onChange={(v) => updateTotal(parseInt(v, 10))}
          ariaLabel="Total value"
        />
        <InlineEditable
          value={target.unit ?? ""}
          placeholder="unit"
          onChange={(v) => onUpdate({ unit: v || undefined } as Partial<Target>)}
          ariaLabel="Unit"
          className="ml-0.5"
        />
        <div className="text-muted-foreground font-normal text-xs ml-2 flex items-center gap-1 opacity-70 hover:opacity-100 transition-opacity">
          <span>(from</span>
          <InlineEditable
            value={String(target.start ?? 0)}
            numeric
            min={0}
            onChange={(v) => onUpdate({ start: parseInt(v, 10) } as Partial<Target>)}
            ariaLabel="Start value"
          />
          <span>)</span>
        </div>
      </div>
      {/* Single progress bar with ± controls; percentage sits inline before the + */}
      <div className="flex items-center gap-3">
        <button
          onClick={() => updateCurrent(Math.max(0, target.current - 1))}
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
          onClick={() => updateCurrent(target.current + 1)}
          className="h-9 w-9 grid place-items-center rounded-md border-2 border-border hover:border-primary hover:text-primary"
          aria-label="Increment"
        >
          <Plus className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

function InlineEditable({
  value,
  onChange,
  placeholder,
  ariaLabel,
  numeric,
  min,
  className,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  ariaLabel: string;
  numeric?: boolean;
  min?: number;
  className?: string;
}) {
  const ref = useRef<HTMLSpanElement>(null);

  // Sync from props if not focused to handle external updates safely
  useEffect(() => {
    if (ref.current && document.activeElement !== ref.current) {
      ref.current.textContent = value;
    }
  }, [value]);

  const handleBlur = (e: React.FocusEvent<HTMLSpanElement>) => {
    let text = e.currentTarget.textContent || "";
    if (numeric) {
      text = text.replace(/[^0-9]/g, "");
      if (text === "") text = "0";
      const n = parseInt(text, 10);
      text = String(typeof min === "number" ? Math.max(min, n) : n);
    }
    
    if (e.currentTarget.textContent !== text) {
      e.currentTarget.textContent = text;
    }
    
    // Only trigger onChange if value actually changed
    if (text !== value) {
      onChange(text);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLSpanElement>) => {
    if (e.key === "Enter") {
      e.preventDefault();
      e.currentTarget.blur();
    }
  };

  return (
    <span
      ref={ref}
      contentEditable
      suppressContentEditableWarning
      role="textbox"
      aria-label={ariaLabel}
      onBlur={handleBlur}
      onKeyDown={handleKeyDown}
      data-placeholder={placeholder}
      className={cn(
        "outline-none cursor-text transition-shadow min-w-[1ch] inline-block empty:before:content-[attr(data-placeholder)] empty:before:text-muted-foreground/75",
        className
      )}
    />
  );
}

const TASKS_MIN_WIDTH = 420;
const TASKS_RESIZE_KEY = "spira:tasks-panel-width";
const TASKS_DEFAULT_WIDTH = 600;

function TasksResizableSheet({
  open,
  onClose,
  items,
  title,
  onChange,
}: {
  open: boolean;
  onClose: () => void;
  items: { id: string; text: string; done: boolean; deadline?: string }[];
  title: string;
  onChange: (items: { id: string; text: string; done: boolean; deadline?: string }[]) => void;
}) {
  const [width, setWidth] = useState<number>(() => {
    if (typeof window === "undefined") return TASKS_DEFAULT_WIDTH;
    const stored = Number(window.localStorage.getItem(TASKS_RESIZE_KEY));
    return stored >= TASKS_MIN_WIDTH ? stored : TASKS_DEFAULT_WIDTH;
  });
  const draggingRef = useRef(false);
  const [isDragging, setIsDragging] = useState(false);
  const handleRef = useRef<HTMLDivElement>(null);
  const isMobile = useIsMobile();
  const compact = isMobile;

  useEffect(() => {
    const onResize = () => setWidth((w) => Math.min(w, window.innerWidth));
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(TASKS_RESIZE_KEY, String(width));
  }, [width]);

  const startDrag = (e: React.PointerEvent) => {
    e.preventDefault();
    draggingRef.current = true;
    setIsDragging(true);
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
    const onMove = (ev: PointerEvent) => {
      if (!draggingRef.current) return;
      const next = Math.max(TASKS_MIN_WIDTH, Math.min(window.innerWidth, window.innerWidth - ev.clientX));
      setWidth(next);
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

  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent
        side="right"
        className={cn("p-0 flex flex-col bg-surface border-l hairline !max-w-none", isDragging && "[&_iframe]:pointer-events-none")}
        style={{ width: `${width}px` }}
      >
        <div
          ref={handleRef}
          onPointerDown={startDrag}
          className="resize-handle"
          role="separator"
          aria-orientation="vertical"
          aria-label="Resize panel"
        />
        <div className="flex-1 flex flex-col overflow-hidden">
          <div className={cn("flex items-center justify-between bg-surface z-10 sticky top-0", compact ? "px-3 pt-3 pb-1" : "px-6 pt-5 pb-2")}>
            <h3 className={cn("font-bold truncate flex-1 min-w-0 pr-2", compact && "text-sm")}>{title}</h3>
            <button
              onClick={onClose}
              className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary shrink-0"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
          <div className={cn("flex-1 overflow-y-auto", compact ? "px-2 pb-2 pt-0" : "px-6 pb-6 pt-0")}>
            <ChecklistEditor items={items} onChange={onChange} compact={compact} hideCountdown={compact} />
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}

function ChecklistEditor({
  items,
  onChange,
  compact = false,
  hideCountdown = false,
}: {
  items: { id: string; text: string; done: boolean; deadline?: string }[];
  onChange: (items: { id: string; text: string; done: boolean; deadline?: string }[]) => void;
  compact?: boolean;
  hideCountdown?: boolean;
}) {
  const [draft, setDraft] = useState("");
  const uid = () => Math.random().toString(36).slice(2, 9);
  return (
    <div className={cn("space-y-1", !compact && "mt-4")}>
      {items.map((it) => (
        <div
          id={`task-${it.id}`}
          key={it.id}
          className={cn(
            "flex scroll-mt-24 items-stretch overflow-hidden rounded-md border transition-colors group/task",
            compact ? "min-h-[40px]" : "min-h-[44px]",
            it.done ? "border-primary" : "border-border hover:border-primary/50",
          )}
        >
          <button
            onClick={() =>
              onChange(items.map((i) => (i.id === it.id ? { ...i, done: !i.done } : i)))
            }
            className={cn(
              "shrink-0 flex items-center justify-center border-r transition-colors",
              compact ? "w-10" : "w-12",
              it.done ? "bg-primary-soft border-primary" : "bg-surface border-border hover:bg-secondary/50",
            )}
          >
            <div className={cn(
              "h-4 w-4 rounded-sm border-2 grid place-items-center transition-colors",
              it.done ? "bg-primary border-primary" : "border-border-strong",
            )}>
              {it.done && <Check className="h-3 w-3 text-primary-foreground" strokeWidth={3} />}
            </div>
          </button>

          <div className={cn(
            "flex-1 flex items-center min-w-0 gap-1 relative bg-surface",
            compact ? "px-2 py-1" : "px-3 py-1.5"
          )}>
            <InlineText
              value={it.text}
              onChange={(text) =>
                onChange(items.map((i) => (i.id === it.id ? { ...i, text } : i)))
              }
              ariaLabel="Edit subtask"
              className={cn(
                "flex-1 text-sm truncate",
                it.done && "line-through text-muted-foreground",
              )}
            />
            <DeadlinePopover
              iso={it.deadline}
              variant="icon"
              hideDaysLeft={hideCountdown}
              onChange={(next) =>
                onChange(items.map((i) => (i.id === it.id ? { ...i, deadline: next } : i)))
              }
            />
            <button
              onClick={() => onChange(items.filter((i) => i.id !== it.id))}
              className="text-muted-foreground hover:text-destructive p-1 rounded hover:bg-secondary shrink-0"
              aria-label="Remove subtask"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      ))}
      <div className={cn(
        "flex items-stretch overflow-hidden rounded-md border border-border bg-surface transition-colors focus-within:border-primary",
        compact ? "mt-2 min-h-[40px]" : "mt-4 min-h-[44px]"
      )}>
        <div className={cn(
          "shrink-0 flex items-center justify-center border-r border-border bg-secondary/30",
          compact ? "w-10" : "w-12"
        )}>
          <span className="h-4 w-4 rounded-sm border-2 border-dashed border-border-strong/70" />
        </div>
        <div className={cn(
          "flex-1 flex items-center relative",
          compact ? "px-2" : "px-3"
        )}>
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
            className="flex-1 bg-transparent outline-none text-base min-h-[40px] placeholder:text-muted-foreground/75"
          />
        </div>
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
  const [start, setStart] = useState(0);
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
        start,
        current: start,
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
      <div className="px-7 pt-6 pb-2 flex items-center justify-between sticky top-0 bg-surface z-10">
        <h2 className="font-sans font-bold text-lg">New target</h2>
        <button
          onClick={onDone}
          className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:bg-secondary"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      <div className="px-7 pt-2 pb-6 space-y-6 overflow-y-auto flex-1">
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
                  "w-full text-left flex items-stretch overflow-hidden rounded-md border transition-colors group",
                  type === opt.v
                    ? "border-primary bg-surface"
                    : "border-border bg-surface hover:border-primary/50",
                )}
              >
                <div
                  className={cn(
                    "w-12 shrink-0 flex items-center justify-center border-r transition-colors",
                    type === opt.v ? "bg-primary-soft border-primary" : "bg-surface border-border group-hover:bg-secondary/50",
                  )}
                >
                  <span
                    className={cn(
                      "h-5 w-5 rounded-full border-2 grid place-items-center transition-colors",
                      type === opt.v ? "border-primary" : "border-border-strong",
                    )}
                  >
                    {type === opt.v && <span className="h-2.5 w-2.5 rounded-full bg-primary" />}
                  </span>
                </div>
                <div className="flex-1 px-4 py-3">
                  <span className="block font-semibold text-sm text-foreground">{opt.t}</span>
                  <span className="block text-xs text-muted-foreground mt-0.5">{opt.d}</span>
                </div>
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
            autoFocus
          />
        </div>
        {type === "numeric" && (
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="text-sm font-semibold block mb-1.5 text-muted-foreground">Start</label>
              <Input
                type="number"
                value={start}
                onChange={(e) => setStart(Number(e.target.value) || 0)}
              />
            </div>
            <div>
              <label className="text-sm font-semibold block mb-1.5">Target</label>
              <Input
                type="number"
                value={total}
                onChange={(e) => setTotal(Number(e.target.value) || 0)}
              />
            </div>
            <div>
              <label className="text-sm font-semibold block mb-1.5 text-muted-foreground">Unit</label>
              <Input
                value={unit}
                onChange={(e) => setUnit(e.target.value)}
                placeholder="apps…"
              />
            </div>
          </div>
        )}
        <div>
          <label className="text-sm font-semibold block mb-1.5">
            Deadline <span className="text-muted-foreground font-normal">(optional)</span>
          </label>
          <DeadlinePopover
            iso={deadline}
            onChange={(next) => setDeadline(next ?? "")}
            variant="input"
            className="w-full justify-start text-left font-normal"
          />
        </div>
      </div>

      <div className="px-7 py-4 flex items-center justify-end gap-3 bg-surface">
        <button onClick={onDone} className="h-11 px-5 rounded-md border-2 border-border text-foreground font-semibold text-sm hover:bg-secondary transition-colors">
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

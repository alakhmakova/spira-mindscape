import { useState } from "react";
import { X } from "lucide-react";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { useIsMobile } from "@/hooks/use-mobile";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetTitle,
} from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ConfidenceStepper } from "./Confidence";
import { useSpira } from "@/lib/spira/store";
import type { Confidence } from "@/lib/spira/types";
import { DeadlinePopover } from "./DeadlinePopover";

function FormBody({ onDone }: { onDone: () => void }) {
  const addGoal = useSpira((s) => s.addGoal);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [confidence, setConfidence] = useState<Confidence>(5);
  const [deadline, setDeadline] = useState<string>("");

  const submit = (publish = true) => {
    if (!title.trim()) return;
    addGoal({
      title: title.trim(),
      description,
      confidence,
      deadline: deadline ? new Date(deadline).toISOString() : undefined,
    });
    onDone();
    void publish;
  };

  return (
    <>
      {/* Header — sticky */}
      <div className="px-7 pt-6 pb-2 flex items-center justify-between sticky top-0 bg-surface z-10">
        <h2 className="font-sans font-bold text-lg text-foreground">
          New goal
        </h2>
        <button
          type="button"
          onClick={onDone}
          className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:text-foreground hover:bg-secondary"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* Body */}
      <div
        id="new-goal-scroll-container"
        className="px-6 pt-2 pb-8 space-y-6 overflow-y-auto flex-1 min-h-0"
      >
        <Field label="Title" required>
          <Input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Launch Spira to first 50 users"
            className="text-base"
          />
        </Field>

        <Field
          label="Description"
          hint="Specific, measurable, achievable, relevant, time-bound."
        >
          <Textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="What does success look like?"
            className="min-h-28"
          />
        </Field>

        <Field
          label="Confidence"
          required
          hintRight={
            <span className="num font-semibold text-foreground">
              {confidence}/10
            </span>
          }
        >
          <div className="pt-1">
            <ConfidenceStepper
              value={confidence}
              onChange={(v) => setConfidence(v as Confidence)}
            />
          </div>
        </Field>

        <Field label="Deadline">
          <DeadlinePopover
            iso={deadline}
            onChange={(next) => setDeadline(next ?? "")}
            variant="input"
          />
        </Field>
      </div>

      {/* Footer — Cancel + Create, pinned to the bottom. */}
      <div
        className="shrink-0 bg-surface px-6 pt-3 flex gap-3"
        style={{ paddingBottom: "max(env(safe-area-inset-bottom), 12px)" }}
      >
        <button
          type="button"
          onClick={onDone}
          className="flex-1 h-12 rounded-md border-2 border-border text-foreground font-semibold text-[15px] hover:bg-secondary transition-colors"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={() => submit(true)}
          disabled={!title.trim()}
          className="flex-1 h-12 rounded-md bg-primary text-primary-foreground font-semibold text-[15px] hover:bg-primary/90 disabled:opacity-40 transition-colors"
        >
          Create goal
        </button>
      </div>
    </>
  );
}

function Field({
  label,
  hint,
  hintRight,
  required,
  children,
}: {
  label: string;
  hint?: string;
  hintRight?: React.ReactNode;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div>
      <div className="flex items-center justify-between mb-1.5">
        <label className="text-sm font-semibold text-foreground">
          {label}
          {required && <span className="text-destructive ml-0.5">*</span>}
        </label>
        {hintRight}
      </div>
      {children}
      {hint && <p className="text-xs text-muted-foreground mt-1.5">{hint}</p>}
    </div>
  );
}

export function NewGoalSheet({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
}) {
  const isMobile = useIsMobile();

  if (isMobile) {
    return (
      <Drawer open={open} onOpenChange={onOpenChange}>
        <DrawerContent className="mt-0 px-0 h-[92vh] max-h-[92vh] flex flex-col">
          <FormBody onDone={() => onOpenChange(false)} />
        </DrawerContent>
      </Drawer>
    );
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="w-full sm:max-w-xl p-0 flex flex-col bg-surface border-l hairline"
      >
        <SheetTitle className="sr-only">New goal</SheetTitle>
        <SheetDescription className="sr-only">
          Create a new Spira goal.
        </SheetDescription>
        <FormBody onDone={() => onOpenChange(false)} />
      </SheetContent>
    </Sheet>
  );
}

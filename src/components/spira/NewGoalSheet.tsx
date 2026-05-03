import { useState } from "react";
import { X, Trophy } from "lucide-react";
import { Drawer, DrawerContent } from "@/components/ui/drawer";
import { useIsMobile } from "@/hooks/use-mobile";
import { Sheet, SheetContent } from "@/components/ui/sheet";
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
          onClick={onDone}
          className="h-8 w-8 grid place-items-center rounded-md text-muted-foreground hover:text-foreground hover:bg-secondary"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* Body */}
      <div id="new-goal-scroll-container" className="px-7 pt-2 pb-[400px] space-y-6 overflow-y-auto flex-1">
        <Field label="Title" required>
          <Input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Launch Spira to first 50 users"
            className="text-base"
            autoFocus
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
          hintRight={<span className="num font-semibold text-foreground">{confidence}/10</span>}
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

      {/* Footer — sticky */}
      <div className="px-7 py-4 flex items-center justify-end gap-3 bg-surface">
        <button onClick={onDone} className="link-action h-11 px-4 text-sm font-semibold">
          Cancel
        </button>
        <button
          onClick={() => submit(true)}
          disabled={!title.trim()}
          className="h-11 px-5 rounded-md bg-primary text-primary-foreground font-semibold text-sm hover:bg-primary/90 disabled:opacity-40"
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
        <DrawerContent className="px-0 pb-6 max-h-[92vh] flex flex-col">
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
        <FormBody onDone={() => onOpenChange(false)} />
      </SheetContent>
    </Sheet>
  );
}

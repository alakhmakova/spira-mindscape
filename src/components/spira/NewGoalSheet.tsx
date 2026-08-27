import { useState } from "react";
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
import { FIELD_LIMITS, lengthError } from "@/lib/spira/limits";
import { cn } from "@/lib/utils";
import { DeadlinePopover } from "./DeadlinePopover";
import { SheetHead } from "./SheetHead";

function FormBody({ onDone }: { onDone: () => void }) {
  const addGoal = useSpira((s) => s.addGoal);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [confidence, setConfidence] = useState<Confidence>(5);
  const [deadline, setDeadline] = useState<string>("");

  const titleMessage = lengthError(title, FIELD_LIMITS.goalTitle, "Title");
  const descriptionMessage = lengthError(
    description,
    FIELD_LIMITS.goalDescription,
    "Description",
  );
  const canSubmit = !!title.trim() && !titleMessage && !descriptionMessage;

  const submit = (publish = true) => {
    if (!canSubmit) return;
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
      <SheetHead title="New goal" onClose={onDone} />

      {/* Body */}
      <div
        id="new-goal-scroll-container"
        className="px-5 pt-4 pb-8 space-y-6 overflow-y-auto flex-1 min-h-0"
      >
        <Field
          label="Title"
          required
          hintRight={
            title.length >= FIELD_LIMITS.goalTitle - 20 ? (
              <span
                className={cn(
                  "num text-xs tabular-nums",
                  title.length >= FIELD_LIMITS.goalTitle
                    ? "text-destructive font-semibold"
                    : "text-muted-foreground",
                )}
              >
                {title.length}/{FIELD_LIMITS.goalTitle}
              </span>
            ) : undefined
          }
        >
          <Input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Launch Spira to first 50 users"
            className="text-base"
          />
          {titleMessage && (
            <p
              className="text-xs font-medium text-destructive mt-1.5"
              role="alert"
            >
              {titleMessage}
            </p>
          )}
        </Field>

        <Field
          label="Description"
          hint="Specific, measurable, achievable, relevant, time-bound."
          hintRight={
            description.length >= FIELD_LIMITS.goalDescription - 100 ? (
              <span
                className={cn(
                  "num text-xs tabular-nums",
                  description.length >= FIELD_LIMITS.goalDescription
                    ? "text-destructive font-semibold"
                    : "text-muted-foreground",
                )}
              >
                {description.length}/{FIELD_LIMITS.goalDescription}
              </span>
            ) : undefined
          }
        >
          <Textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="What does success look like?"
            className="min-h-28"
          />
          {descriptionMessage && (
            <p
              className="text-xs font-medium text-destructive mt-1.5"
              role="alert"
            >
              {descriptionMessage}
            </p>
          )}
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
        className="shrink-0 bg-surface px-5 pt-3 flex gap-3"
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
          disabled={!canSubmit}
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
        <DrawerContent className="mt-0 px-0 h-[92dvh] max-h-[92dvh] flex flex-col">
          <FormBody onDone={() => onOpenChange(false)} />
        </DrawerContent>
      </Drawer>
    );
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        // The head carries its own white X on the teal band; the corner one would sit on it.
        closeButton={false}
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

import { useState } from "react";
import { Drawer, DrawerContent, DrawerHeader, DrawerTitle } from "@/components/ui/drawer";
import { useIsMobile } from "@/hooks/use-mobile";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { ConfidenceStepper } from "./Confidence";
import { useSpira } from "@/lib/spira/store";
import type { Confidence } from "@/lib/spira/types";

function FormBody({ onDone }: { onDone: () => void }) {
  const addGoal = useSpira((s) => s.addGoal);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [confidence, setConfidence] = useState<Confidence>(5);
  const [deadline, setDeadline] = useState<string>("");

  const submit = () => {
    if (!title.trim()) return;
    addGoal({
      title: title.trim(),
      description,
      confidence,
      deadline: deadline ? new Date(deadline).toISOString() : undefined,
    });
    onDone();
  };

  return (
    <div className="space-y-5 px-1">
      <div>
        <label className="text-xs text-muted-foreground uppercase tracking-wider">
          What do you want to achieve?
        </label>
        <Input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="e.g. Launch Spira to first 50 users"
          className="mt-1.5 bg-surface border-border text-base h-11"
          autoFocus
        />
      </div>
      <div>
        <label className="text-xs text-muted-foreground uppercase tracking-wider">
          Description (SMART)
        </label>
        <Textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Specific, measurable, achievable, relevant, time-bound."
          className="mt-1.5 bg-surface border-border min-h-24"
        />
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label className="text-xs text-muted-foreground uppercase tracking-wider">
            Deadline
          </label>
          <Input
            type="date"
            value={deadline}
            onChange={(e) => setDeadline(e.target.value)}
            className="mt-1.5 bg-surface border-border h-11"
          />
        </div>
        <div>
          <label className="text-xs text-muted-foreground uppercase tracking-wider flex justify-between">
            <span>Confidence</span>
            <span className="num">{confidence}/10</span>
          </label>
          <div className="mt-2">
            <ConfidenceStepper value={confidence} onChange={(v) => setConfidence(v as Confidence)} />
          </div>
        </div>
      </div>
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="ghost" onClick={onDone}>
          Cancel
        </Button>
        <Button onClick={submit} disabled={!title.trim()}>
          Create goal
        </Button>
      </div>
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
        <DrawerContent className="px-4 pb-6">
          <DrawerHeader className="px-0">
            <DrawerTitle className="font-display text-2xl">New goal</DrawerTitle>
          </DrawerHeader>
          <FormBody onDone={() => onOpenChange(false)} />
        </DrawerContent>
      </Drawer>
    );
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full sm:max-w-lg flex flex-col">
        <SheetHeader>
          <SheetTitle className="font-display text-2xl">New goal</SheetTitle>
        </SheetHeader>
        <div className="mt-4 flex-1 overflow-y-auto">
          <FormBody onDone={() => onOpenChange(false)} />
        </div>
      </SheetContent>
    </Sheet>
  );
}

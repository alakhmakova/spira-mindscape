import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import type React from "react";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * Centered confirmation dialog. White card, hairline border, drop-shadow.
 * Cancel = neutral outlined ("No, go back"), Confirm = solid red destructive —
 * or solid teal (`tone="primary"`) when the action creates something rather than removes it.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = "Yes, remove",
  cancelLabel = "No, go back",
  tone = "destructive",
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  title: string;
  /** Plain text, or a node when the explanation needs an icon or emphasis inside it. */
  description: React.ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  /** "destructive" (default) = red confirm; "primary" = teal, for a constructive action. */
  tone?: "destructive" | "primary";
  onConfirm: () => void;
}) {
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="bg-surface border-0 rounded-lg shadow-2xl w-[calc(100%-2rem)] max-w-[440px] sm:max-w-[600px] p-6">
        <button
          onClick={() => onOpenChange(false)}
          className="absolute right-5 top-5 text-muted-foreground/70 hover:text-foreground transition-colors"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>

        <AlertDialogHeader className="space-y-2 text-left pr-8">
          <AlertDialogTitle className="font-sans font-semibold text-[20px] text-foreground tracking-tight">
            {title}
          </AlertDialogTitle>
          <AlertDialogDescription className="text-[14px] text-foreground/80 leading-relaxed">
            {description}
          </AlertDialogDescription>
        </AlertDialogHeader>

        <AlertDialogFooter className="mt-6 gap-3 sm:gap-3 flex-row justify-end">
          <AlertDialogCancel className="mt-0 h-10 px-5 rounded-md border border-border/80 bg-surface text-foreground font-semibold hover:bg-secondary">
            {cancelLabel}
          </AlertDialogCancel>
          <AlertDialogAction
            className={cn(
              "h-10 px-5 rounded-md text-white font-semibold",
              tone === "primary"
                ? "bg-primary hover:bg-primary/90"
                : "bg-[#d13239] hover:bg-[#b0292f]",
            )}
            onClick={onConfirm}
          >
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

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
import { X } from "lucide-react";

/**
 * Centered confirmation dialog. White card, hairline border, drop-shadow.
 * Cancel = neutral outlined ("No, go back"), Confirm = solid red destructive.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = "Yes, remove",
  cancelLabel = "No, go back",
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  onConfirm: () => void;
}) {
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="bg-surface border-0 rounded-lg shadow-2xl max-w-[640px] p-6">
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
            className="h-10 px-5 rounded-md bg-[#d13239] text-white font-semibold hover:bg-[#b0292f]"
            onClick={onConfirm}
          >
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

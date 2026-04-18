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
      <AlertDialogContent className="bg-surface border hairline rounded-lg shadow-2xl max-w-lg p-7">
        <AlertDialogHeader className="space-y-3 text-left">
          <AlertDialogTitle className="font-sans font-bold text-xl text-foreground tracking-tight">
            {title}
          </AlertDialogTitle>
          <AlertDialogDescription className="text-[15px] text-foreground/70 leading-relaxed">
            {description}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter className="mt-5 gap-3 sm:gap-3 flex-row justify-end">
          <AlertDialogCancel className="mt-0 h-11 px-5 rounded-md border-2 border-foreground/15 bg-surface text-foreground font-semibold hover:bg-secondary">
            {cancelLabel}
          </AlertDialogCancel>
          <AlertDialogAction
            className="h-11 px-5 rounded-md bg-destructive text-destructive-foreground font-semibold hover:bg-destructive/90"
            onClick={onConfirm}
          >
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

import * as AlertDialogPrimitive from "@radix-ui/react-alert-dialog";
import { useNavigate } from "@tanstack/react-router";
import { useRef, useState } from "react";
import { Bell } from "@/components/spira/icons";
import type { DueReminder } from "@/lib/spira/api";
import {
  reminderWhen,
  useDueReminders,
  type ReminderAnswer,
} from "@/lib/spira/due-reminders";
import { UPCOMING_CATEGORIES } from "@/lib/spira/upcoming";

/**
 * **Decide now** — a reminder that has gone off takes the whole of Spira until it is answered
 * (plan §5a, owner 2026-10-10: for every Upcoming event).
 *
 * A browser cannot draw over the phone or the desktop, so this is the most a web page can do: it
 * covers the app itself. It is an alert dialog with **no X**, and neither Escape nor a click
 * outside it closes it. There are three ways out and each one is an answer:
 *
 * - **Done** — settled.
 * - **Snooze 10 min** — quiet for ten minutes, then it comes back here (and as a notification).
 * - **Open** — settled, and the goal it belongs to opens (an important date opens Upcoming).
 *
 * Its look is for later, as agreed; for now it is a Spira surface in the app's own parts.
 */
export function DecideNow({
  reminder,
  remaining = 0,
  onAnswered,
}: {
  reminder: DueReminder;
  /** How many more are waiting behind this one. */
  remaining?: number;
  /** Called after an answer the server accepted — the route variant navigates away. */
  onAnswered?: (action: ReminderAnswer) => void;
}) {
  const answer = useDueReminders((s) => s.answer);
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const doneRef = useRef<HTMLButtonElement>(null);

  const kind =
    UPCOMING_CATEGORIES.find((c) => c.kind === reminder.kind)?.singular ??
    "Important date";
  const when = reminderWhen(reminder);

  const respond = async (action: ReminderAnswer) => {
    if (busy) return;
    setBusy(true);
    const ok = await answer(reminder.eventId, action);
    setBusy(false);
    if (!ok) return;
    if (action === "open") {
      if (reminder.goalId) {
        void navigate({
          to: "/goals/$goalId",
          params: { goalId: reminder.goalId },
        });
      } else {
        void navigate({ to: "/" });
      }
    }
    onAnswered?.(action);
  };

  return (
    <AlertDialogPrimitive.Root open>
      <AlertDialogPrimitive.Portal>
        <AlertDialogPrimitive.Content
          onEscapeKeyDown={(e) => e.preventDefault()}
          // Radix's alert dialog focuses its Cancel part, and this page has none: Done is the
          // answer a keyboard user should land on.
          onOpenAutoFocus={(e) => {
            e.preventDefault();
            doneRef.current?.focus();
          }}
          className="fixed inset-0 z-[60] flex flex-col bg-white focus:outline-none"
          data-testid="decide-now"
        >
          <div className="flex shrink-0 items-center gap-2 bg-primary px-5 py-4 text-primary-foreground">
            <Bell aria-hidden="true" className="h-4 w-4" />
            <span className="text-base font-bold">Reminder</span>
            {remaining > 0 ? (
              <span className="ml-auto text-sm text-primary-foreground/85">
                {remaining} more waiting
              </span>
            ) : null}
          </div>

          <div className="flex min-h-0 flex-1 flex-col items-center justify-center overflow-y-auto px-5 py-10 text-center">
            <p className="text-sm font-semibold uppercase tracking-wide text-primary">
              {kind}
            </p>
            <AlertDialogPrimitive.Title className="mt-3 max-w-xl break-words font-heading text-4xl leading-[1.1] tracking-tight text-foreground sm:text-5xl">
              {reminder.title}
            </AlertDialogPrimitive.Title>
            <AlertDialogPrimitive.Description className="mt-4 text-lg text-foreground/80">
              {when || "It’s time to decide."}
            </AlertDialogPrimitive.Description>

            <div className="mt-10 flex w-full max-w-sm flex-col gap-3">
              <button
                ref={doneRef}
                type="button"
                disabled={busy}
                onClick={() => void respond("done")}
                className="h-12 rounded-md bg-primary text-[15px] font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-40"
              >
                Done
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={() => void respond("snooze")}
                className="h-12 rounded-md border-2 border-border text-[15px] font-semibold text-foreground transition-colors hover:bg-secondary disabled:opacity-40"
              >
                Snooze 10 min
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={() => void respond("open")}
                className="h-12 rounded-md border-2 border-border text-[15px] font-semibold text-foreground transition-colors hover:bg-secondary disabled:opacity-40"
              >
                {reminder.goalId ? "Open goal" : "Open Upcoming"}
              </button>
            </div>
          </div>
        </AlertDialogPrimitive.Content>
      </AlertDialogPrimitive.Portal>
    </AlertDialogPrimitive.Root>
  );
}

/**
 * Mounted once for every signed-in page: the longest-waiting due reminder covers the app. The
 * `/remind/$eventId` route draws its own, for the event a notification was tapped for.
 */
export function DecideNowGate({ suppressed }: { suppressed: boolean }) {
  const due = useDueReminders((s) => s.due);
  if (suppressed || due.length === 0) return null;
  return (
    <DecideNow
      key={due[0].eventId}
      reminder={due[0]}
      remaining={due.length - 1}
    />
  );
}

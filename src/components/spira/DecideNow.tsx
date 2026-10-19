import * as AlertDialogPrimitive from "@radix-ui/react-alert-dialog";
import { useRef, useState } from "react";
import { Bell } from "@/components/spira/icons";
import { Input } from "@/components/ui/input";
import type { DueReminder } from "@/lib/spira/api";
import {
  DEFAULT_SNOOZE,
  reminderWhen,
  snoozeAmountError,
  snoozeLabel,
  SNOOZE_UNITS,
  useDueReminders,
  type ReminderAnswer,
  type Snooze,
  type SnoozeUnit,
} from "@/lib/spira/due-reminders";
import { useEventDecision } from "@/lib/spira/event-decision";
import { UPCOMING_CATEGORIES } from "@/lib/spira/upcoming";
import { cn } from "@/lib/utils";
import { FIELD } from "./ReminderFields";

/**
 * **Decide now** — a reminder that has gone off takes the whole of Spira until it is answered
 * (plan §5a, owner 2026-10-10: for every Upcoming event).
 *
 * A browser cannot draw over the phone or the desktop, so this is the most a web page can do: it
 * covers the app itself. It is an alert dialog with **no X**, and neither Escape nor a click
 * outside it closes it. Every way out is an answer, and which ones it offers depends on WHEN the
 * reminder is (owner, 2026-10-19):
 *
 * - **Ahead of the event's day** it only informs, so there is nothing to mark done yet:
 *   **I know** lets this reminder go (the next one, if any, still comes) or **Snooze…**.
 * - **On the day** it asks for a decision: **Snooze…** or **Open**, which brings up the event's
 *   own card — Done, Won't do or Postpone (`EventDecision.tsx`).
 *
 * **Snooze…** sets how long: any number from 1 to 999 and its unit, minutes to months
 * (`SnoozePicker`); then it comes back here, and as a notification. The notification's own Snooze
 * opens this page on that picker rather than snoozing blind.
 *
 * Its look is for later, as agreed; for now it is a Spira surface in the app's own parts.
 */
export function DecideNow({
  reminder,
  remaining = 0,
  initialSnooze = false,
  onAnswered,
}: {
  reminder: DueReminder;
  /** How many more are waiting behind this one. */
  remaining?: number;
  /** Open straight on the snooze lengths — the notification's Snooze button lands here. */
  initialSnooze?: boolean;
  /** Called after an answer the server accepted — the route variant navigates away. */
  onAnswered?: (action: ReminderAnswer) => void;
}) {
  const answer = useDueReminders((s) => s.answer);
  const openEvent = useEventDecision((s) => s.open);
  const [busy, setBusy] = useState(false);
  const [choosingSnooze, setChoosingSnooze] = useState(initialSnooze);
  const doneRef = useRef<HTMLButtonElement>(null);

  const kind =
    UPCOMING_CATEGORIES.find((c) => c.kind === reminder.kind)?.singular ??
    "Important date";
  const when = reminderWhen(reminder);

  const respond = async (action: ReminderAnswer, snooze?: Snooze) => {
    if (busy) return;
    setBusy(true);
    const ok = await answer(reminder.eventId, action, snooze);
    setBusy(false);
    if (!ok) return;
    if (action === "open") openEvent(reminder.eventId);
    onAnswered?.(action);
  };

  return (
    <AlertDialogPrimitive.Root open>
      <AlertDialogPrimitive.Portal>
        <AlertDialogPrimitive.Content
          onEscapeKeyDown={(e) => e.preventDefault()}
          // Radix's alert dialog focuses its Cancel part, and this page has none: the first answer
          // (I know, or Open on the day) is where a keyboard user should land.
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

            {choosingSnooze ? (
              <SnoozePicker
                busy={busy}
                onSnooze={(snooze) => void respond("snooze", snooze)}
                onBack={() => setChoosingSnooze(false)}
              />
            ) : (
              <div className="mt-10 flex w-full max-w-sm flex-col gap-3">
                {reminder.onTheDay ? (
                  <button
                    ref={doneRef}
                    type="button"
                    disabled={busy}
                    onClick={() => void respond("open")}
                    className="h-12 rounded-md bg-primary text-[15px] font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-40"
                  >
                    Open
                  </button>
                ) : (
                  <button
                    ref={doneRef}
                    type="button"
                    disabled={busy}
                    onClick={() => void respond("dismiss")}
                    className="h-12 rounded-md bg-primary text-[15px] font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-40"
                  >
                    I know
                  </button>
                )}
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => setChoosingSnooze(true)}
                  className="h-12 rounded-md border-2 border-border text-[15px] font-semibold text-foreground transition-colors hover:bg-secondary disabled:opacity-40"
                >
                  Snooze…
                </button>
              </div>
            )}
          </div>
        </AlertDialogPrimitive.Content>
      </AlertDialogPrimitive.Portal>
    </AlertDialogPrimitive.Root>
  );
}

/**
 * **How long to snooze** — a number and its unit, both typed or picked by the user: "2 hours",
 * "3 days", "1 month". It opens on {@link DEFAULT_SNOOZE}, so a plain tap on the button still
 * snoozes for ten minutes; the button names what it will do.
 */
function SnoozePicker({
  busy,
  onSnooze,
  onBack,
}: {
  busy: boolean;
  onSnooze: (snooze: Snooze) => void;
  onBack: () => void;
}) {
  const [amount, setAmount] = useState(String(DEFAULT_SNOOZE.amount));
  const [unit, setUnit] = useState<SnoozeUnit>(DEFAULT_SNOOZE.unit);
  const [touched, setTouched] = useState(false);
  const refs = useRef<(HTMLButtonElement | null)[]>([]);

  const error = snoozeAmountError(amount);
  const showError = touched && error;
  const snooze = error ? undefined : { amount: Number(amount), unit };

  const submit = () => {
    if (!snooze) {
      setTouched(true);
      return;
    }
    onSnooze(snooze);
  };

  const index = SNOOZE_UNITS.findIndex((u) => u.unit === unit);
  const move = (delta: number) => {
    const next = (index + delta + SNOOZE_UNITS.length) % SNOOZE_UNITS.length;
    setUnit(SNOOZE_UNITS[next].unit);
    refs.current[next]?.focus();
  };

  return (
    <div
      role="group"
      aria-labelledby="snooze-heading"
      className="mt-10 flex w-full max-w-sm flex-col gap-5 text-left"
    >
      <p
        id="snooze-heading"
        className="text-base font-semibold leading-tight text-foreground"
      >
        Snooze for
      </p>
      <div>
        <label
          htmlFor="snooze-amount"
          className="mb-2 block text-sm font-semibold text-foreground"
        >
          Number
        </label>
        <Input
          id="snooze-amount"
          inputMode="numeric"
          autoComplete="off"
          value={amount}
          onChange={(e) =>
            setAmount(e.target.value.replace(/[^\d]/g, "").slice(0, 3))
          }
          onBlur={() => setTouched(true)}
          onKeyDown={(e) => {
            if (e.key === "Enter") submit();
          }}
          aria-invalid={showError ? true : undefined}
          aria-describedby={showError ? "snooze-amount-error" : undefined}
          className={cn(FIELD, "w-28")}
        />
        {showError ? (
          <p
            id="snooze-amount-error"
            role="alert"
            className="mt-1.5 text-xs font-medium text-destructive"
          >
            {error}
          </p>
        ) : null}
      </div>

      <div>
        <span
          id="snooze-unit-label"
          className="mb-2 block text-sm font-semibold text-foreground"
        >
          Unit
        </span>
        <div
          role="radiogroup"
          aria-labelledby="snooze-unit-label"
          className="space-y-1"
        >
          {SNOOZE_UNITS.map((u, i) => {
            const selected = u.unit === unit;
            return (
              <button
                key={u.unit}
                ref={(el) => {
                  refs.current[i] = el;
                }}
                type="button"
                role="radio"
                aria-checked={selected}
                tabIndex={selected ? 0 : -1}
                onClick={() => setUnit(u.unit)}
                onKeyDown={(e) => {
                  if (e.key === "ArrowDown" || e.key === "ArrowRight") {
                    e.preventDefault();
                    move(1);
                  } else if (e.key === "ArrowUp" || e.key === "ArrowLeft") {
                    e.preventDefault();
                    move(-1);
                  }
                }}
                className="flex w-full items-center gap-3 rounded-[4px] py-2 text-left text-base text-foreground focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring"
              >
                {/* The custom reminder's radio (ReminderFields.tsx): a 20px circle with a 1px
                    grey edge; chosen, a 2px Kale ring with a Kale dot. */}
                <span
                  aria-hidden="true"
                  className={cn(
                    "grid h-5 w-5 shrink-0 place-items-center rounded-full bg-white",
                    selected
                      ? "border-2 border-primary"
                      : "border border-[#9F9F9F]",
                  )}
                >
                  {selected ? (
                    <span className="h-2.5 w-2.5 rounded-full bg-primary" />
                  ) : null}
                </span>
                {u.many[0].toUpperCase() + u.many.slice(1)}
              </button>
            );
          })}
        </div>
      </div>

      <div className="flex flex-col gap-3">
        <button
          type="button"
          disabled={busy || !snooze}
          onClick={submit}
          className="h-12 rounded-md bg-primary text-[15px] font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-40"
        >
          {snooze ? `Snooze for ${snoozeLabel(snooze)}` : "Snooze"}
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={onBack}
          className="self-center text-sm font-semibold text-primary underline-offset-4 hover:underline disabled:opacity-40"
        >
          Back
        </button>
      </div>
    </div>
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

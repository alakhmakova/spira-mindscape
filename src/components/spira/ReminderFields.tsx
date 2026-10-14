import { useEffect, useId, useRef, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectSeparator,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { CirclePlus, X } from "@/components/spira/icons";
import { useIsMobile } from "@/hooks/use-mobile";
import {
  DEFAULT_MINUTE,
  amountError,
  MAX_REMINDERS,
  deviceTimeZone,
  minuteToTime,
  presetsFor,
  reminderKey,
  reminderLabel,
  reminderSentence,
  sameReminder,
  timeToMinute,
  type Reminder,
  type ReminderUnit,
} from "@/lib/spira/reminders";
import { pushState, type PushState } from "@/lib/spira/web-push";
import { cn } from "@/lib/utils";
import { SheetHead } from "./SheetHead";

/** The Add event form's field frame — 40px, a 1px `#9F9F9F` edge, 4px corners (CLAUDE.md 3g). */
export const FIELD =
  "h-10 rounded-[4px] border-[#9F9F9F] px-3 text-base focus-visible:border-primary";

const LABEL =
  "mb-3 block text-base font-semibold leading-tight text-foreground";

/**
 * **All day, or at a time** — every Upcoming date may have one (owner, 2026-10-10). All day is
 * the default; switching it off shows the time field. The time is the Upcoming entry's own: a
 * linked deadline stays a day everywhere else in the app.
 */
export function EventTimeField({
  allDay,
  time,
  onAllDayChange,
  onTimeChange,
}: {
  allDay: boolean;
  time: string;
  onAllDayChange: (allDay: boolean) => void;
  onTimeChange: (time: string) => void;
}) {
  return (
    <div className="flex flex-wrap items-end gap-x-6 gap-y-3">
      <div className="flex h-10 items-center gap-3">
        <Switch
          id="event-all-day"
          checked={allDay}
          onCheckedChange={onAllDayChange}
        />
        <label
          htmlFor="event-all-day"
          className="cursor-pointer text-base font-semibold text-foreground"
        >
          All day
        </label>
      </div>
      {allDay ? null : (
        <div>
          <label htmlFor="event-time" className={LABEL}>
            Time
          </label>
          <Input
            id="event-time"
            type="time"
            value={time}
            onChange={(e) => onTimeChange(e.target.value)}
            className={cn(FIELD, "w-36")}
          />
        </div>
      )}
    </div>
  );
}

const CUSTOM = "custom";

/**
 * **Reminders** — the rows, the "Add reminder" link and the sentence that says when they arrive.
 *
 * Each row is a Spira `Select` of the presets for this kind of date plus "Custom…", with an X to
 * remove it. At most five; the link goes at five. The sentence under them is recomputed as the
 * date, the time or the rows change, so the default reminder is never invisible (owner,
 * 2026-10-10). Its time zone is on hover / long-press.
 */
export function RemindersField({
  reminders,
  onChange,
  timed,
  date,
  time,
}: {
  reminders: Reminder[];
  onChange: (next: Reminder[]) => void;
  timed: boolean;
  date: string | undefined;
  time: string | undefined;
}) {
  const presets = presetsFor(timed);
  // Which row the Custom dialog edits; `reminders.length` means a new row.
  const [customFor, setCustomFor] = useState<number | null>(null);
  const triggers = useRef<(HTMLButtonElement | null)[]>([]);
  const sentenceId = useId();
  const zone = deviceTimeZone();
  const push = usePushState();

  const replace = (index: number, next: Reminder) => {
    const list = reminders.map((r, i) => (i === index ? next : r));
    // A duplicate of another row collapses into it rather than being stored twice.
    onChange(
      list.filter((r, i) => list.findIndex((o) => sameReminder(o, r)) === i),
    );
  };
  const remove = (index: number) =>
    onChange(reminders.filter((_, i) => i !== index));
  const addOne = () => {
    const free = presets.find(
      (p) => !reminders.some((r) => sameReminder(r, p)),
    );
    if (free) onChange([...reminders, free]);
    else setCustomFor(reminders.length);
  };

  return (
    <div>
      <span id={`${sentenceId}-label`} className={LABEL}>
        Reminders
      </span>
      {reminders.length > 0 ? (
        <ul className="space-y-2" aria-labelledby={`${sentenceId}-label`}>
          {reminders.map((r, i) => {
            const key = reminderKey(r);
            const options = presets.filter(
              (p) =>
                sameReminder(p, r) ||
                !reminders.some((o) => sameReminder(o, p)),
            );
            const custom = !presets.some((p) => sameReminder(p, r));
            const label = reminderLabel(r, timed);
            return (
              <li key={key} className="flex items-center gap-2">
                <Select
                  value={key}
                  onValueChange={(value) => {
                    if (value === CUSTOM) {
                      setCustomFor(i);
                      return;
                    }
                    const picked = presets.find(
                      (p) => reminderKey(p) === value,
                    );
                    if (picked) replace(i, picked);
                  }}
                >
                  <SelectTrigger
                    ref={(el) => {
                      triggers.current[i] = el;
                    }}
                    aria-label={`Reminder ${i + 1}: ${label}`}
                    className={cn(
                      FIELD,
                      "min-w-0 flex-1 shadow-none [&>span]:truncate",
                    )}
                  >
                    <SelectValue>{label}</SelectValue>
                  </SelectTrigger>
                  <SelectContent className="bg-white">
                    {custom ? (
                      <SelectItem value={key} className="py-2 text-[15px]">
                        {label}
                      </SelectItem>
                    ) : null}
                    {options.map((p) => (
                      <SelectItem
                        key={reminderKey(p)}
                        value={reminderKey(p)}
                        className="py-2 text-[15px]"
                      >
                        {reminderLabel(p, timed)}
                      </SelectItem>
                    ))}
                    <SelectSeparator />
                    <SelectItem value={CUSTOM} className="py-2 text-[15px]">
                      Custom…
                    </SelectItem>
                  </SelectContent>
                </Select>
                <button
                  type="button"
                  onClick={() => remove(i)}
                  aria-label={`Remove reminder: ${label}`}
                  className="grid h-10 w-10 shrink-0 place-items-center rounded-[4px] text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
                >
                  <X className="h-4 w-4" />
                </button>
              </li>
            );
          })}
        </ul>
      ) : null}

      {reminders.length < MAX_REMINDERS ? (
        <button
          type="button"
          onClick={addOne}
          className={cn(
            "inline-flex items-center gap-2 py-1 text-[15px] font-medium text-primary transition-opacity hover:opacity-80",
            reminders.length > 0 && "mt-3",
          )}
        >
          <CirclePlus aria-hidden="true" className="h-4 w-4 shrink-0" />
          <span className="underline decoration-1 underline-offset-[3px]">
            Add reminder
          </span>
        </button>
      ) : null}

      <p
        id={sentenceId}
        aria-live="polite"
        title={zone ? `Times are in ${zone}` : undefined}
        className="mt-3 text-pretty text-sm leading-relaxed text-foreground/80"
      >
        {reminderSentence(date, time, reminders)}
      </p>
      {reminders.length > 0 && PUSH_NOTE[push] ? (
        <p className="mt-1 text-pretty text-sm leading-relaxed text-muted-foreground">
          {PUSH_NOTE[push]}
        </p>
      ) : null}

      <CustomReminderDialog
        open={customFor !== null}
        timed={timed}
        initial={
          customFor !== null && customFor < reminders.length
            ? reminders[customFor]
            : undefined
        }
        onCancel={() => setCustomFor(null)}
        onDone={(r) => {
          if (customFor === null) return;
          if (customFor >= reminders.length) {
            if (!reminders.some((o) => sameReminder(o, r)))
              onChange([...reminders, r]);
          } else {
            replace(customFor, r);
          }
          setCustomFor(null);
        }}
      />
    </div>
  );
}

/**
 * What this browser will do with a reminder, said under the sentence when it is not the obvious
 * "a notification arrives". Permission is asked on Save (plan §5), never on page load.
 */
const PUSH_NOTE: Partial<Record<PushState, string>> = {
  ask: "Saving will ask this browser to show notifications, so reminders reach you even with Spira closed.",
  denied:
    "Notifications are blocked for Spira in this browser, so reminders will show only inside Spira.",
  unsupported:
    "This browser can’t show notifications, so reminders will show only inside Spira. On an iPhone, add Spira to the Home Screen first.",
};

function usePushState(): PushState {
  const [state, setState] = useState<PushState>("off");
  useEffect(() => {
    let live = true;
    void pushState().then((s) => {
      if (live) setState(s);
    });
    return () => {
      live = false;
    };
  }, []);
  return state;
}

const UNITS: { unit: ReminderUnit; label: string; timedOnly: boolean }[] = [
  { unit: "minute", label: "Minutes before", timedOnly: true },
  { unit: "hour", label: "Hours before", timedOnly: true },
  { unit: "day", label: "Days before", timedOnly: false },
  { unit: "week", label: "Weeks before", timedOnly: false },
];

/**
 * **Custom reminder** — a number, a unit and, on an all-day date, the time of day. Nothing is
 * saved here: Done writes the row into the form, and the form's own Save stores it.
 *
 * A centred modal on both widths. On a phone it is the date picker's shape (CLAUDE.md 3e-ter): a
 * value picked inside a form that is already open is a modal, not a second sheet stacked on the
 * first — so it wears the Kale head and the app's pinned foot there, and the Add event card's own
 * white head on a laptop.
 */
export function CustomReminderDialog({
  open,
  timed,
  initial,
  onCancel,
  onDone,
}: {
  open: boolean;
  timed: boolean;
  initial?: Reminder;
  onCancel: () => void;
  onDone: (reminder: Reminder) => void;
}) {
  const isMobile = useIsMobile();
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onCancel();
      }}
    >
      <DialogContent
        closeButton={false}
        className={cn(
          "modal-max flex w-[calc(100%-32px)] flex-col gap-0 overflow-hidden border-0 p-0",
          isMobile
            ? "max-w-[380px] rounded-xl"
            : "max-w-[440px] rounded-[4px] bg-white shadow-2xl sm:rounded-[4px]",
        )}
      >
        {open ? (
          <CustomReminderForm
            mobile={isMobile}
            timed={timed}
            initial={initial}
            onCancel={onCancel}
            onDone={onDone}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function CustomReminderForm({
  mobile,
  timed,
  initial,
  onCancel,
  onDone,
}: {
  mobile: boolean;
  timed: boolean;
  initial?: Reminder;
  onCancel: () => void;
  onDone: (reminder: Reminder) => void;
}) {
  const units = UNITS.filter((u) => timed || !u.timedOnly);
  const startUnit =
    initial && units.some((u) => u.unit === initial.unit)
      ? initial.unit
      : timed
        ? "minute"
        : "day";
  const [amount, setAmount] = useState(
    initial && initial.amount > 0 ? String(initial.amount) : "1",
  );
  const [unit, setUnit] = useState<ReminderUnit>(startUnit);
  const [at, setAt] = useState(
    minuteToTime(initial?.atMinuteOfDay ?? DEFAULT_MINUTE),
  );
  const [touched, setTouched] = useState(false);
  const refs = useRef<(HTMLButtonElement | null)[]>([]);

  const error = amountError(amount);
  const atMinute = timeToMinute(at);
  const valid = !error && (timed || atMinute !== undefined);
  const showError = touched && error;

  const done = () => {
    if (!valid) {
      setTouched(true);
      return;
    }
    onDone({
      amount: Number(amount),
      unit,
      atMinuteOfDay: timed ? undefined : atMinute,
    });
  };

  const index = units.findIndex((u) => u.unit === unit);
  const move = (delta: number) => {
    const next = (index + delta + units.length) % units.length;
    setUnit(units[next].unit);
    refs.current[next]?.focus();
  };

  const body = (
    <div className="space-y-5">
      <div>
        <label htmlFor="custom-reminder-amount" className={LABEL}>
          Number
        </label>
        <Input
          id="custom-reminder-amount"
          inputMode="numeric"
          autoComplete="off"
          value={amount}
          onChange={(e) =>
            setAmount(e.target.value.replace(/[^\d]/g, "").slice(0, 3))
          }
          onBlur={() => setTouched(true)}
          onKeyDown={(e) => {
            if (e.key === "Enter") done();
          }}
          aria-invalid={showError ? true : undefined}
          aria-describedby={
            showError ? "custom-reminder-amount-error" : undefined
          }
          className={cn(FIELD, "w-28")}
        />
        {showError ? (
          <p
            id="custom-reminder-amount-error"
            role="alert"
            className="mt-1.5 text-xs font-medium text-destructive"
          >
            {error}
          </p>
        ) : null}
      </div>

      <div>
        <span id="custom-reminder-unit-label" className={LABEL}>
          Unit
        </span>
        <div
          role="radiogroup"
          aria-labelledby="custom-reminder-unit-label"
          className="space-y-1"
        >
          {units.map((u, i) => {
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
                {/* The Edit compensation radio: a 20px circle with a 1px grey edge; chosen, a 2px
                    Kale ring with a Kale dot. */}
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
                {u.label}
              </button>
            );
          })}
        </div>
      </div>

      {timed ? null : (
        <div>
          <label htmlFor="custom-reminder-at" className={LABEL}>
            At
          </label>
          <Input
            id="custom-reminder-at"
            type="time"
            value={at}
            onChange={(e) => setAt(e.target.value)}
            className={cn(FIELD, "w-36")}
          />
        </div>
      )}
    </div>
  );

  if (mobile) {
    return (
      <>
        <SheetHead
          title="Custom reminder"
          onClose={onCancel}
          titleComponent={DialogTitle}
        />
        <DialogDescription className="sr-only">
          Choose how long before the date the reminder arrives.
        </DialogDescription>
        <div className="min-h-0 flex-1 overflow-y-auto px-5 pb-6 pt-4">
          {body}
        </div>
        <div
          className="flex shrink-0 gap-3 bg-surface px-5 pt-3"
          style={{ paddingBottom: "max(env(safe-area-inset-bottom), 12px)" }}
        >
          <button
            type="button"
            onClick={onCancel}
            className="h-12 flex-1 rounded-md border-2 border-border text-[15px] font-semibold text-foreground transition-colors hover:bg-secondary"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={done}
            disabled={!valid}
            className="h-12 flex-1 rounded-md bg-primary text-[15px] font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-40"
          >
            Done
          </button>
        </div>
      </>
    );
  }

  return (
    <div className="flex min-h-0 flex-col p-5">
      <div className="flex items-start gap-4">
        <DialogTitle className="min-w-0 flex-1 font-sans text-[20px] font-semibold leading-7 tracking-tight text-foreground">
          Custom reminder
        </DialogTitle>
        <button
          type="button"
          onClick={onCancel}
          className="-mr-1.5 grid h-7 w-7 shrink-0 place-items-center rounded-md text-muted-foreground/70 transition-colors hover:bg-secondary hover:text-foreground"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
      <DialogDescription className="mt-3 text-[15px] leading-relaxed text-foreground/80">
        Choose how long before the date the reminder arrives.
      </DialogDescription>
      <div className="mt-5 min-h-0 overflow-y-auto">{body}</div>
      <div className="mt-7 flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="h-12 rounded-[4px] border border-[#9F9F9F] bg-white px-6 text-base font-semibold text-foreground transition-colors hover:bg-secondary"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={done}
          disabled={!valid}
          className="h-12 rounded-[4px] bg-primary px-6 text-base font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-40"
        >
          Done
        </button>
      </div>
    </div>
  );
}

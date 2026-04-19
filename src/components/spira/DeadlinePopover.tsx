import { useState } from "react";
import { Calendar as CalendarIcon, Trash2, X } from "lucide-react";
import { format, isPast, differenceInCalendarDays } from "date-fns";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import { cn } from "@/lib/utils";

/**
 * Editable deadline trigger. Click opens a popover with a calendar
 * (ISO week numbers shown) and a Remove action.
 *
 * Visual: small inline pill — calendar icon, formatted date, and a
 * compact "Xd left / overdue" annotation. When no date is set, shows
 * "Set deadline" placeholder.
 */
export function DeadlinePopover({
  iso,
  onChange,
  size = "sm",
  align = "start",
}: {
  iso?: string;
  onChange: (next: string | undefined) => void;
  size?: "sm" | "md";
  align?: "start" | "center" | "end";
}) {
  const [open, setOpen] = useState(false);
  const date = iso ? new Date(iso) : undefined;
  const days = date ? differenceInCalendarDays(date, new Date()) : 0;
  const overdue = !!date && isPast(date) && days < 0;

  const tone = !date
    ? "text-muted-foreground"
    : overdue
      ? "text-destructive"
      : days <= 7
        ? "text-[oklch(0.45_0.12_60)]"
        : "text-foreground/80";

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        className={cn(
          "inline-flex items-center gap-1.5 rounded-md border hairline bg-surface hover:border-primary/40 hover:bg-primary-soft/50 transition-colors num font-medium",
          size === "md" ? "h-9 px-3 text-sm" : "h-7 px-2 text-xs",
          tone,
        )}
      >
        <CalendarIcon className={cn(size === "md" ? "h-4 w-4" : "h-3 w-3", "opacity-70")} />
        {date ? (
          <>
            <span>{format(date, "MMM d, yyyy")}</span>
            <span className="opacity-60 font-normal">
              ·{" "}
              {overdue
                ? `${Math.abs(days)}d overdue`
                : days === 0
                  ? "today"
                  : `${days}d left`}
            </span>
          </>
        ) : (
          <span>Set deadline</span>
        )}
      </PopoverTrigger>
      <PopoverContent align={align} className="w-auto p-0 bg-surface border hairline shadow-lg">
        <div className="flex items-center justify-between px-3 py-2 border-b hairline">
          <span className="text-xs uppercase tracking-wider font-semibold text-muted-foreground">
            Pick a date
          </span>
          <button
            onClick={() => setOpen(false)}
            className="h-6 w-6 grid place-items-center rounded text-muted-foreground hover:bg-secondary"
            aria-label="Close"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
        <Calendar
          mode="single"
          selected={date}
          onSelect={(d) => {
            onChange(d ? d.toISOString() : undefined);
            setOpen(false);
          }}
          showWeekNumber
          weekStartsOn={1}
          ISOWeek
          initialFocus
        />
        {date && (
          <div className="px-3 py-2 border-t hairline flex justify-end">
            <button
              onClick={() => {
                onChange(undefined);
                setOpen(false);
              }}
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-destructive hover:text-destructive/80"
            >
              <Trash2 className="h-3 w-3" /> Remove deadline
            </button>
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}
